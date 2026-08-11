package com.odoomaster.ticketing.sales;

import com.odoomaster.ticketing.shared.Auditable;
import com.odoomaster.ticketing.catalog.EventCatalog;
import com.odoomaster.ticketing.catalog.EventCatalog.EventSummary;
import com.odoomaster.ticketing.catalog.SeatInventory;
import com.odoomaster.ticketing.catalog.SeatInventory.SeatDetail;
import com.odoomaster.ticketing.ticketing.TicketIssuance;
import com.odoomaster.ticketing.ticketing.TicketIssuance.TicketLine;
import com.odoomaster.ticketing.ticketing.TicketIssuance.TicketOrder;
import com.odoomaster.ticketing.sales.OrderDtos.*;
import com.odoomaster.ticketing.sales.internal.Order;
import com.odoomaster.ticketing.sales.internal.OrderStatus;
import com.odoomaster.ticketing.sales.internal.OrderItem;
import com.odoomaster.ticketing.sales.internal.Payment;
import com.odoomaster.ticketing.sales.internal.OrderRepository;
import com.odoomaster.ticketing.sales.internal.OrderItemRepository;
import com.odoomaster.ticketing.sales.internal.PaymentRepository;
import com.odoomaster.ticketing.shared.TicketsIssuedEvent;
import com.odoomaster.ticketing.shared.AppException;
import com.odoomaster.ticketing.sales.payment.PaymentGateway;
import com.odoomaster.ticketing.sales.payment.PaymentMethod;
import com.odoomaster.ticketing.sales.payment.PaymentGatewayResolver;
import com.odoomaster.ticketing.sales.payment.PaymentRequest;
import com.odoomaster.ticketing.sales.payment.PaymentResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Core ordering service and the concurrency crux of the system.
 *
 * <p>Orchestrates the sale as a single transaction while delegating the concurrency-critical seat
 * work to catalog's {@link SeatInventory} (which owns the {@code AVAILABLE → LOCKED → SOLD} state
 * machine, the lock TTL, and event-cache eviction) and ticket persistence to ticketing's
 * {@link TicketIssuance}. Event reads go through catalog's {@link EventCatalog}, so this module no
 * longer touches the {@code Event}/{@code EventSeat}/{@code Ticket} entities of other modules.
 *
 * <p>Payment is delegated to a {@link PaymentGateway} chosen by {@link PaymentGatewayResolver}
 * (Strategy + Factory), and ticket issuance fires a {@link TicketsIssuedEvent} that
 * {@code NotificationEventListener} observes — keeping payment and notification concerns out of
 * this class.
 */
@Service
public class OrderService {

    private final EventCatalog eventCatalog;
    private final SeatInventory seatInventory;
    private final TicketIssuance ticketIssuance;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final PaymentRepository payments;

    private final PaymentGatewayResolver paymentGatewayResolver;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(EventCatalog eventCatalog, SeatInventory seatInventory,
                        TicketIssuance ticketIssuance,
                        OrderRepository orders, OrderItemRepository orderItems,
                        PaymentRepository payments,
                        PaymentGatewayResolver paymentGatewayResolver,
                        ApplicationEventPublisher eventPublisher) {
        this.eventCatalog = eventCatalog;
        this.seatInventory = seatInventory;
        this.ticketIssuance = ticketIssuance;
        this.orders = orders;
        this.orderItems = orderItems;
        this.payments = payments;
        this.paymentGatewayResolver = paymentGatewayResolver;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Create a PENDING order and hold its seats via {@link SeatInventory#lockSeats}.
     *
     * <p>Validates the event is on sale and the requested seats are non-empty and unique, then hands
     * the seats to catalog to lock (which enforces availability/cross-event rules, sets the lock TTL,
     * and evicts the event caches). Runs in one transaction.
     *
     * @param userId the buyer holding the seats
     * @param req the event id and requested seat ids
     * @return a view of the created order with its line items
     * @throws AppException if the event is missing/not published, seats are duplicated/empty, or the
     *                      lock is rejected (missing, cross-event, or already taken)
     */
    @Transactional
    @Auditable(action = "ORDER_CREATED", entity = "orders")
    public OrderView create(Long userId, CreateOrderRequest req) {
        EventSummary event = eventCatalog.requireOnSale(req.eventId());
        if (req.seatIds() == null || req.seatIds().isEmpty()) {
            throw new AppException("VALIDATION_FAILED", "At least one seat is required.", HttpStatus.BAD_REQUEST);
        }
        Set<Long> unique = new HashSet<>(req.seatIds());
        if (unique.size() != req.seatIds().size()) {
            throw new AppException("DUPLICATE_SEATS", "Duplicate seats in request.", HttpStatus.BAD_REQUEST);
        }

        // Catalog locks the seats (availability/cross-event checks, lock TTL, cache eviction) and
        // returns their priced details.
        List<SeatDetail> picked = seatInventory.lockSeats(event.id(), userId, req.seatIds());

        BigDecimal total = picked.stream()
                .map(SeatDetail::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .userId(userId)
                .eventId(event.id())
                .totalAmount(total)
                .status(OrderStatus.PENDING)
                .build();
        orders.save(order);

        List<OrderItem> items = new ArrayList<>();
        for (SeatDetail s : picked) {
            items.add(OrderItem.builder()
                    .orderId(order.getId())
                    .eventSeatId(s.id())
                    .ticketTypeId(s.ticketTypeId())
                    .price(s.price())
                    .build());
        }
        orderItems.saveAll(items);

        return toView(order, event, items, picked);
    }

    /**
     * Pay a PENDING order: mark its seats {@code SOLD} via {@link SeatInventory#markSold}, charge the
     * gateway, issue QR tickets via {@link TicketIssuance}, and publish a {@link TicketsIssuedEvent}.
     *
     * <p>Idempotent for an already-PAID order (returns the existing view without re-issuing). The
     * whole method is transactional; the seat mutation and its cache eviction commit atomically with
     * the payment, tickets, and order update.
     *
     * @param userId the paying user (must own the order)
     * @param orderId the order to pay
     * @param req the chosen payment method/provider
     * @return a view of the paid order
     * @throws AppException if the order is missing, not owned by the user, in an unpayable state,
     *                      or a seat is no longer holdable
     */
    @Transactional
    @Auditable(action = "ORDER_PAID", entity = "orders")
    public OrderView pay(Long userId, Long orderId, PayRequest req) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new AppException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND));
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new AppException("FORBIDDEN", "Order does not belong to current user.", HttpStatus.FORBIDDEN);
        }
        if (order.getStatus() == OrderStatus.PAID) {
            return view(order);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new AppException("ORDER_STATE_INVALID", "Order cannot be paid in state " + order.getStatus(), HttpStatus.CONFLICT);
        }

        // The wire contract keeps `method` a String (see PaymentMethod's javadoc); parse it here so a
        // bad value is a 400 VALIDATION_FAILED rather than a Jackson 500.
        PaymentMethod method = PaymentMethod.parse(req.method())
                .orElseThrow(() -> new AppException("VALIDATION_FAILED",
                        "Unsupported payment method: " + req.method(), HttpStatus.BAD_REQUEST));

        List<OrderItem> items = orderItems.findByOrderId(order.getId());
        List<Long> seatIds = items.stream().map(OrderItem::getEventSeatId).toList();

        // Catalog re-checks the seats and transitions them to SOLD (clearing locks, evicting caches).
        seatInventory.markSold(order.getEventId(), seatIds);

        // Charge via the resolved payment gateway (Strategy). The mock gateway always succeeds.
        PaymentGateway gateway = paymentGatewayResolver.resolve(method);
        PaymentResult result = gateway.charge(new PaymentRequest(order.getId(), method, order.getTotalAmount()));
        Payment p = Payment.builder()
                .orderId(order.getId())
                .provider(method)
                .transactionId(result.transactionId())
                .amount(order.getTotalAmount())
                .status(result.status())
                .build();
        payments.save(p);

        Instant now = Instant.now();
        order.setStatus(OrderStatus.PAID);
        order.setPaymentMethod(method);
        order.setPaidAt(now);
        orders.save(order);

        // Ticketing issues one VALID QR ticket per line, within this transaction.
        List<TicketLine> lines = items.stream()
                .map(item -> new TicketLine(item.getId(), item.getEventSeatId()))
                .toList();
        int ticketCount = ticketIssuance.issueForOrder(new TicketOrder(userId, order.getEventId(), lines));

        // Publish the tickets-issued event (Observer); NotificationEventListener creates the
        // in-app notification. Runs synchronously within this transaction.
        String eventTitle = eventCatalog.find(order.getEventId()).map(EventSummary::title).orElse(null);
        eventPublisher.publishEvent(new TicketsIssuedEvent(
                userId, order.getId(), eventTitle, ticketCount, method.name()));

        return view(order);
    }

    @Transactional
    public void cancel(Long userId, Long orderId) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new AppException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND));
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new AppException("FORBIDDEN", "Order does not belong to current user.", HttpStatus.FORBIDDEN);
        }
        if (order.getStatus() == OrderStatus.PAID) {
            throw new AppException("ORDER_ALREADY_PAID", "Cannot cancel a paid order.", HttpStatus.CONFLICT);
        }
        if (order.getStatus() == OrderStatus.CANCELLED) return;

        List<OrderItem> items = orderItems.findByOrderId(order.getId());
        List<Long> seatIds = items.stream().map(OrderItem::getEventSeatId).toList();
        // Catalog releases any still-LOCKED seats back to AVAILABLE (and evicts caches).
        seatInventory.releaseLocks(order.getEventId(), seatIds);
        orderItems.deleteAll(items);
        order.setStatus(OrderStatus.CANCELLED);
        orders.save(order);
    }

    @Transactional(readOnly = true)
    public List<OrderView> listMine(Long userId) {
        return orders.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public OrderView getMine(Long userId, Long orderId) {
        Order o = orders.findById(orderId)
                .orElseThrow(() -> new AppException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND));
        if (!Objects.equals(o.getUserId(), userId)) {
            throw new AppException("FORBIDDEN", "Order does not belong to current user.", HttpStatus.FORBIDDEN);
        }
        return view(o);
    }

    private OrderView view(Order order) {
        EventSummary ev = eventCatalog.find(order.getEventId()).orElse(null);
        List<OrderItem> items = orderItems.findByOrderId(order.getId());
        List<SeatDetail> picked = seatInventory.findSeats(items.stream().map(OrderItem::getEventSeatId).toList());
        return toView(order, ev, items, picked);
    }

    private OrderView toView(Order order, EventSummary event, List<OrderItem> items, List<SeatDetail> picked) {
        Map<Long, SeatDetail> byId = new HashMap<>();
        for (SeatDetail s : picked) byId.put(s.id(), s);
        List<OrderItemView> rows = items.stream().map(it -> {
            SeatDetail s = byId.get(it.getEventSeatId());
            return new OrderItemView(it.getId(), it.getEventSeatId(),
                    s != null ? s.rowLabel() : null,
                    s != null ? s.seatNumber() : null,
                    s != null ? s.section() : null,
                    it.getPrice());
        }).toList();
        return new OrderView(order.getId(), order.getEventId(),
                event != null ? event.title() : null,
                order.getStatus().name(),
                order.getPaymentMethod() == null ? null : order.getPaymentMethod().name(),
                order.getTotalAmount(), order.getCreatedAt(), order.getPaidAt(), rows);
    }
}
