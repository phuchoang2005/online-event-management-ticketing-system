package com.odoomaster.ticketing.feedback;

import com.odoomaster.ticketing.catalog.EventCatalog;
import com.odoomaster.ticketing.catalog.EventCatalog.EventSummary;
import com.odoomaster.ticketing.feedback.FeedbackDtos.*;
import com.odoomaster.ticketing.iam.UserDirectory;
import com.odoomaster.ticketing.iam.UserDirectory.UserRef;
import com.odoomaster.ticketing.shared.AppException;
import com.odoomaster.ticketing.feedback.internal.Feedback;
import com.odoomaster.ticketing.feedback.internal.FeedbackCategory;
import com.odoomaster.ticketing.feedback.internal.FeedbackStatus;
import com.odoomaster.ticketing.feedback.internal.FeedbackRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;

/**
 * Customer feedback service: submission by users and listing/triage (status + admin notes) by staff.
 *
 * <p>User and event context on a feedback view is resolved through iam's {@link UserDirectory} and
 * catalog's {@link EventCatalog}, so this module no longer touches the {@code User}/{@code Event}
 * entities or their repositories.
 */
@Service
public class FeedbackService {

    private static final Set<FeedbackCategory> VALID_CATEGORIES = EnumSet.allOf(FeedbackCategory.class);
    private static final Set<FeedbackStatus> VALID_STATUSES = EnumSet.allOf(FeedbackStatus.class);

    private final FeedbackRepository feedbacks;
    private final UserDirectory users;
    private final EventCatalog events;

    public FeedbackService(FeedbackRepository feedbacks, UserDirectory users, EventCatalog events) {
        this.feedbacks = feedbacks;
        this.users = users;
        this.events = events;
    }

    @Transactional
    public FeedbackView submit(Long userId, SubmitFeedbackRequest req) {
        if (req.subject() == null || req.subject().isBlank()) {
            throw new AppException("VALIDATION_FAILED", "Subject is required.", HttpStatus.BAD_REQUEST);
        }
        if (req.body() == null || req.body().isBlank()) {
            throw new AppException("VALIDATION_FAILED", "Body is required.", HttpStatus.BAD_REQUEST);
        }
        FeedbackCategory cat = req.category() == null
                ? FeedbackCategory.GENERAL
                : FeedbackCategory.parse(req.category())
                        .orElseThrow(() -> new AppException("VALIDATION_FAILED",
                                "Invalid category.", HttpStatus.BAD_REQUEST));
        if (req.rating() != null && (req.rating() < 1 || req.rating() > 5)) {
            throw new AppException("VALIDATION_FAILED", "Rating must be 1–5.", HttpStatus.BAD_REQUEST);
        }

        Feedback fb = Feedback.builder()
                .userId(userId)
                .eventId(req.eventId())
                .category(cat)
                .subject(req.subject().trim())
                .body(req.body().trim())
                .rating(req.rating())
                .status(FeedbackStatus.NEW)
                .build();
        feedbacks.save(fb);

        UserRef user = users.find(userId).orElse(null);
        EventSummary event = req.eventId() != null ? events.find(req.eventId()).orElse(null) : null;
        return toView(fb, user, event);
    }

    @Transactional(readOnly = true)
    public FeedbackPage list(int page, int limit, String status, String category) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.min(100, Math.max(1, limit));
        FeedbackStatus nStatus = FeedbackStatus.parse(status).orElse(null);
        FeedbackCategory nCat = FeedbackCategory.parse(category).orElse(null);

        Page<Feedback> result = feedbacks.findAllFiltered(nStatus, nCat,
                PageRequest.of(safePage - 1, safeLimit));
        List<FeedbackView> items = result.getContent().stream().map(this::toView).toList();
        return new FeedbackPage(items, new PageMeta(safePage, safeLimit, result.getTotalElements(), result.hasNext()));
    }

    @Transactional(readOnly = true)
    public FeedbackSummary summary() {
        long total = feedbacks.count();
        long newCount = feedbacks.countByStatus(FeedbackStatus.NEW);
        long readCount = feedbacks.countByStatus(FeedbackStatus.READ);
        long resolvedCount = feedbacks.countByStatus(FeedbackStatus.RESOLVED);
        List<Feedback> all = feedbacks.findAll();
        Double avgRating = all.stream()
                .filter(f -> f.getRating() != null)
                .mapToInt(Feedback::getRating)
                .average()
                .stream().boxed().findFirst().orElse(null);
        return new FeedbackSummary(total, newCount, readCount, resolvedCount, avgRating);
    }

    @Transactional
    public FeedbackView updateStatus(Long feedbackId, UpdateStatusRequest req) {
        Feedback fb = feedbacks.findById(feedbackId)
                .orElseThrow(() -> new AppException("FEEDBACK_NOT_FOUND", "Feedback not found.", HttpStatus.NOT_FOUND));
        FeedbackStatus newStatus = FeedbackStatus.parse(req.status())
                .orElseThrow(() -> new AppException("VALIDATION_FAILED",
                        "Invalid status.", HttpStatus.BAD_REQUEST));
        fb.setStatus(newStatus);
        if (newStatus == FeedbackStatus.RESOLVED) fb.setResolvedAt(Instant.now());
        if (req.adminNote() != null && !req.adminNote().isBlank()) fb.setAdminNote(req.adminNote().trim());
        feedbacks.save(fb);
        return toView(fb);
    }

    private FeedbackView toView(Feedback fb) {
        UserRef user = users.find(fb.getUserId()).orElse(null);
        EventSummary event = fb.getEventId() != null ? events.find(fb.getEventId()).orElse(null) : null;
        return toView(fb, user, event);
    }

    private FeedbackView toView(Feedback fb, UserRef user, EventSummary event) {
        return new FeedbackView(
                fb.getId(),
                fb.getUserId(),
                user != null ? user.email() : null,
                fb.getEventId(),
                event != null ? event.title() : null,
                fb.getCategory().name(),
                fb.getSubject(),
                fb.getBody(),
                fb.getRating(),
                fb.getStatus().name(),
                fb.getCreatedAt(),
                fb.getResolvedAt(),
                fb.getAdminNote());
    }
}
