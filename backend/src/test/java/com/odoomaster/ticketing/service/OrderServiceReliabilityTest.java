package com.odoomaster.ticketing.service;
import com.odoomaster.ticketing.sales.OrderService;
import com.odoomaster.ticketing.notification.NotificationService;

import com.odoomaster.ticketing.catalog.EventCatalog;
import com.odoomaster.ticketing.catalog.EventCatalog.EventSummary;
import com.odoomaster.ticketing.catalog.SeatInventory;
import com.odoomaster.ticketing.catalog.SeatInventory.SeatDetail;
import com.odoomaster.ticketing.ticketing.TicketIssuance;
import com.odoomaster.ticketing.ticketing.TicketIssuance.TicketOrder;
import com.odoomaster.ticketing.sales.internal.Order;
import com.odoomaster.ticketing.sales.internal.OrderStatus;
import com.odoomaster.ticketing.sales.internal.OrderItem;
import com.odoomaster.ticketing.sales.OrderDtos.CreateOrderRequest;
import com.odoomaster.ticketing.sales.OrderDtos.PayRequest;
import com.odoomaster.ticketing.shared.AppException;
import com.odoomaster.ticketing.sales.internal.OrderItemRepository;
import com.odoomaster.ticketing.sales.internal.OrderRepository;
import com.odoomaster.ticketing.sales.internal.PaymentRepository;
import com.odoomaster.ticketing.shared.TicketsIssuedEvent;
import com.odoomaster.ticketing.notification.NotificationEventListener;
import com.odoomaster.ticketing.sales.payment.MockPaymentGateway;
import com.odoomaster.ticketing.sales.payment.PaymentGatewayResolver;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reliability tests for {@link OrderService}'s <em>orchestration</em> of a sale: it now delegates
 * the concurrency-critical seat work to {@link SeatInventory}, ticket persistence to
 * {@link TicketIssuance}, and event reads to {@link EventCatalog}, all mocked here. The seat state
 * machine and QR-uniqueness guarantees that used to live in this class are covered by
 * {@code SeatInventoryReliabilityTest} and {@code TicketIssuanceReliabilityTest}.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceReliabilityTest {

    @Mock EventCatalog eventCatalog;
    @Mock SeatInventory seatInventory;
    @Mock TicketIssuance ticketIssuance;
    @Mock OrderRepository orders;
    @Mock OrderItemRepository orderItems;
    @Mock PaymentRepository payments;
    @Mock NotificationService notificationService;

    OrderService service;

    @BeforeEach
    void setUp() {
        // Wire the Observer end-to-end: route published TicketsIssuedEvents to a real listener
        // backed by the mocked NotificationService, so notification assertions still hold.
        NotificationEventListener listener = new NotificationEventListener(notificationService);
        ApplicationEventPublisher publisher = event -> {
            if (event instanceof TicketsIssuedEvent e) listener.onTicketsIssued(e);
        };
        PaymentGatewayResolver resolver = new PaymentGatewayResolver(List.of(new MockPaymentGateway()));
        service = new OrderService(eventCatalog, seatInventory, ticketIssuance,
                orders, orderItems, payments, resolver, publisher);
    }

    @Test
    void create_givenOnSaleEvent_locksSeatsViaInventoryAndTotalsOrder() {
        when(eventCatalog.requireOnSale(1L)).thenReturn(summary("PUBLISHED"));
        when(seatInventory.lockSeats(1L, 5L, List.of(10L, 11L))).thenReturn(List.of(
                seatDetail(10L, BigDecimal.valueOf(100_000)),
                seatDetail(11L, BigDecimal.valueOf(150_000))));
        when(orders.save(any())).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId(77L);
            return order;
        });
        when(orderItems.saveAll(anyList())).thenAnswer(inv -> {
            List<OrderItem> rows = inv.getArgument(0);
            long id = 1L;
            for (OrderItem row : rows) row.setId(id++);
            return rows;
        });

        var view = service.create(5L, new CreateOrderRequest(1L, List.of(10L, 11L)));

        assertThat(view.id()).isEqualTo(77L);
        assertThat(view.eventTitle()).isEqualTo("Reliability event");
        assertThat(view.totalAmount()).isEqualByComparingTo("250000");
        assertThat(view.items()).hasSize(2);
        // The seat hold (state machine + lock TTL) is delegated to catalog with the buyer + seats.
        verify(seatInventory).lockSeats(1L, 5L, List.of(10L, 11L));
    }

    @Test
    void create_givenEventNotOnSale_rejectsBeforeLocking() {
        when(eventCatalog.requireOnSale(1L)).thenThrow(new AppException(
                "EVENT_NOT_PUBLISHED", "Event is not currently on sale.", HttpStatus.CONFLICT));

        assertThatThrownBy(() -> service.create(5L, new CreateOrderRequest(1L, List.of(10L))))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("not currently on sale");
        verify(seatInventory, never()).lockSeats(anyLong(), anyLong(), anyList());
        verify(orders, never()).save(any());
    }

    @Test
    void create_givenSeatsRejectedByInventory_propagatesAndSavesNoOrder() {
        when(eventCatalog.requireOnSale(1L)).thenReturn(summary("PUBLISHED"));
        when(seatInventory.lockSeats(anyLong(), anyLong(), anyList())).thenThrow(new AppException(
                "SEAT_TAKEN", "Seat A-10 is no longer available.", HttpStatus.CONFLICT));

        assertThatThrownBy(() -> service.create(5L, new CreateOrderRequest(1L, List.of(10L))))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("no longer available");
        verify(orders, never()).save(any());
    }

    @Test
    void create_givenDuplicateSeatIds_rejectsBeforeLocking() {
        when(eventCatalog.requireOnSale(1L)).thenReturn(summary("PUBLISHED"));

        assertThatThrownBy(() -> service.create(5L, new CreateOrderRequest(1L, List.of(10L, 10L))))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Duplicate seats");
        verify(seatInventory, never()).lockSeats(anyLong(), anyLong(), anyList());
    }

    @Test
    void create_givenEmptySeatSelection_rejectsBeforeLocking() {
        when(eventCatalog.requireOnSale(1L)).thenReturn(summary("PUBLISHED"));

        assertThatThrownBy(() -> service.create(5L, new CreateOrderRequest(1L, List.of())))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("At least one seat");
        verify(seatInventory, never()).lockSeats(anyLong(), anyLong(), anyList());
    }

    @Test
    void pay_givenPendingOrder_marksSoldIssuesTicketsAndNotifies() {
        Order order = order(20L, OrderStatus.PENDING, 5L);
        OrderItem item = item(30L, 10L);
        when(orders.findById(20L)).thenReturn(Optional.of(order));
        when(orderItems.findByOrderId(20L)).thenReturn(List.of(item));
        when(ticketIssuance.issueForOrder(any())).thenReturn(1);
        when(eventCatalog.find(1L)).thenReturn(Optional.of(summary("PUBLISHED")));
        lenient().when(seatInventory.findSeats(List.of(10L)))
                .thenReturn(List.of(seatDetail(10L, BigDecimal.TEN)));

        service.pay(5L, 20L, new PayRequest("MOCK"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isNotNull();
        verify(seatInventory).markSold(1L, List.of(10L));
        verify(payments).save(any());
        verify(ticketIssuance).issueForOrder(any());
        verify(notificationService).create(5L, "TICKETS_ISSUED", "Vé của bạn đã được phát hành",
                "Sự kiện: Reliability event. Đơn hàng #20 đã thanh toán thành công qua MOCK.",
                "IN_APP", "/tickets");
    }

    @Test
    void pay_issuesOneTicketLinePerOrderItem() {
        Order order = order(20L, OrderStatus.PENDING, 5L);
        when(orders.findById(20L)).thenReturn(Optional.of(order));
        when(orderItems.findByOrderId(20L)).thenReturn(List.of(item(30L, 10L)));
        when(ticketIssuance.issueForOrder(any())).thenReturn(1);
        when(eventCatalog.find(1L)).thenReturn(Optional.of(summary("PUBLISHED")));
        lenient().when(seatInventory.findSeats(anyList())).thenReturn(List.of());

        service.pay(5L, 20L, new PayRequest("VNPAY"));

        ArgumentCaptor<TicketOrder> captor = ArgumentCaptor.forClass(TicketOrder.class);
        verify(ticketIssuance).issueForOrder(captor.capture());
        TicketOrder issued = captor.getValue();
        assertThat(issued.userId()).isEqualTo(5L);
        assertThat(issued.eventId()).isEqualTo(1L);
        assertThat(issued.lines()).singleElement()
                .satisfies(line -> {
                    assertThat(line.orderItemId()).isEqualTo(30L);
                    assertThat(line.eventSeatId()).isEqualTo(10L);
                });
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "REFUNDED"})
    void pay_givenInvalidOrderState_rejectsPayment(OrderStatus status) {
        when(orders.findById(20L)).thenReturn(Optional.of(order(20L, status, 5L)));

        assertThatThrownBy(() -> service.pay(5L, 20L, new PayRequest("MOCK")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Order cannot be paid");
        verify(seatInventory, never()).markSold(anyLong(), anyList());
    }

    @Test
    void pay_givenAlreadyPaidOrder_isIdempotentAndDoesNotReissue() {
        Order paid = order(20L, OrderStatus.PAID, 5L);
        when(orders.findById(20L)).thenReturn(Optional.of(paid));
        when(eventCatalog.find(1L)).thenReturn(Optional.of(summary("PUBLISHED")));
        when(orderItems.findByOrderId(20L)).thenReturn(List.of());
        when(seatInventory.findSeats(List.of())).thenReturn(List.of());

        service.pay(5L, 20L, new PayRequest("MOCK"));

        verify(seatInventory, never()).markSold(anyLong(), anyList());
        verify(ticketIssuance, never()).issueForOrder(any());
        verify(payments, never()).save(any());
    }

    @Test
    void pay_givenSeatRejectedByInventory_doesNotChargeOrIssueTickets() {
        Order order = order(20L, OrderStatus.PENDING, 5L);
        when(orders.findById(20L)).thenReturn(Optional.of(order));
        when(orderItems.findByOrderId(20L)).thenReturn(List.of(item(30L, 10L)));
        when(seatInventory.markSold(1L, List.of(10L))).thenThrow(new AppException(
                "LOCK_EXPIRED", "Seat lock expired; please re-select seats.", HttpStatus.CONFLICT));

        assertThatThrownBy(() -> service.pay(5L, 20L, new PayRequest("MOCK")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("lock expired");
        verify(payments, never()).save(any());
        verify(ticketIssuance, never()).issueForOrder(any());
    }

    @Test
    void cancel_givenPendingOrder_releasesSeatsAndDeletesItems() {
        Order order = order(20L, OrderStatus.PENDING, 5L);
        when(orders.findById(20L)).thenReturn(Optional.of(order));
        when(orderItems.findByOrderId(20L)).thenReturn(List.of(item(30L, 10L)));

        service.cancel(5L, 20L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(seatInventory).releaseLocks(1L, List.of(10L));
        verify(orderItems).deleteAll(anyList());
    }

    @ParameterizedTest
    @CsvSource({
            "PAID,Cannot cancel a paid order",
            "CANCELLED,"
    })
    void cancel_givenTerminalState_handlesSafely(OrderStatus status, String message) {
        when(orders.findById(20L)).thenReturn(Optional.of(order(20L, status, 5L)));

        if (status == OrderStatus.CANCELLED) {
            service.cancel(5L, 20L);
            verify(seatInventory, never()).releaseLocks(anyLong(), anyList());
        } else {
            assertThatThrownBy(() -> service.cancel(5L, 20L))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining(message);
        }
    }

    @Test
    void getMine_givenOrderOwnedByAnotherUser_rejectsAccess() {
        when(orders.findById(20L)).thenReturn(Optional.of(order(20L, OrderStatus.PENDING, 99L)));

        assertThatThrownBy(() -> service.getMine(5L, 20L))
                .isInstanceOf(AppException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listMine_returnsOrdersInRepositoryOrder() {
        when(orders.findByUserIdOrderByCreatedAtDesc(5L))
                .thenReturn(List.of(order(2L, OrderStatus.PENDING, 5L), order(1L, OrderStatus.PAID, 5L)));
        when(eventCatalog.find(1L)).thenReturn(Optional.of(summary("PUBLISHED")));
        when(orderItems.findByOrderId(anyLong())).thenReturn(List.of());
        when(seatInventory.findSeats(List.of())).thenReturn(List.of());

        assertThat(service.listMine(5L)).extracting("id").containsExactly(2L, 1L);
    }

    private static EventSummary summary(String status) {
        return new EventSummary(1L, "Reliability event", "Main Hall",
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), status);
    }

    private static SeatDetail seatDetail(Long id, BigDecimal price) {
        return new SeatDetail(id, 3L, "A", String.valueOf(id), "MAIN", price, "LOCKED");
    }

    private static Order order(Long id, OrderStatus status, Long userId) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(userId);
        order.setEventId(1L);
        order.setTotalAmount(BigDecimal.TEN);
        order.setStatus(status);
        order.setCreatedAt(Instant.now());
        return order;
    }

    private static OrderItem item(Long id, Long eventSeatId) {
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrderId(20L);
        item.setEventSeatId(eventSeatId);
        item.setTicketTypeId(3L);
        item.setPrice(BigDecimal.TEN);
        return item;
    }
}
