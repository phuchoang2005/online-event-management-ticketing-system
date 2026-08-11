package com.odoomaster.ticketing.service;
import com.odoomaster.ticketing.feedback.FeedbackService;

import com.odoomaster.ticketing.catalog.EventCatalog;
import com.odoomaster.ticketing.feedback.internal.Feedback;
import com.odoomaster.ticketing.feedback.internal.FeedbackCategory;
import com.odoomaster.ticketing.feedback.internal.FeedbackStatus;
import com.odoomaster.ticketing.feedback.FeedbackDtos.*;
import com.odoomaster.ticketing.iam.UserDirectory;
import com.odoomaster.ticketing.iam.UserDirectory.UserRef;
import com.odoomaster.ticketing.shared.AppException;
import com.odoomaster.ticketing.feedback.internal.FeedbackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock FeedbackRepository feedbackRepo;
    @Mock UserDirectory userDirectory;
    @Mock EventCatalog eventCatalog;

    @InjectMocks FeedbackService service;

    // ── submit ──────────────────────────────────────────────────────────────

    @Test
    void submit_happyPath_savesFeedbackWithDefaults() {
        var req = new SubmitFeedbackRequest(null, "GENERAL", "Great app", "I love it", 5);
        when(userDirectory.find(1L)).thenReturn(Optional.of(new UserRef(1L, "user@test.com")));
        when(feedbackRepo.save(any())).thenAnswer(inv -> {
            Feedback f = inv.getArgument(0);
            f.setId(42L);
            return f;
        });

        FeedbackView view = service.submit(1L, req);

        ArgumentCaptor<Feedback> cap = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepo).save(cap.capture());
        Feedback saved = cap.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getCategory()).isEqualTo(FeedbackCategory.GENERAL);
        assertThat(saved.getSubject()).isEqualTo("Great app");
        assertThat(saved.getStatus()).isEqualTo(FeedbackStatus.NEW);
        assertThat(saved.getRating()).isEqualTo(5);
        assertThat(view.id()).isEqualTo(42L);
        assertThat(view.userEmail()).isEqualTo("user@test.com");
    }

    @Test
    void submit_blankSubject_throwsValidationError() {
        var req = new SubmitFeedbackRequest(null, "GENERAL", "  ", "body", null);

        assertThatThrownBy(() -> service.submit(1L, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Subject");
    }

    @Test
    void submit_blankBody_throwsValidationError() {
        var req = new SubmitFeedbackRequest(null, "GENERAL", "subject", "", null);

        assertThatThrownBy(() -> service.submit(1L, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Body");
    }

    @Test
    void submit_invalidRating_throwsValidationError() {
        var req = new SubmitFeedbackRequest(null, "GENERAL", "subject", "body", 6);

        assertThatThrownBy(() -> service.submit(1L, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Rating");
    }

    @Test
    void submit_unknownCategory_throwsValidationError() {
        var req = new SubmitFeedbackRequest(null, "UNKNOWN_CAT", "subject", "body", null);

        assertThatThrownBy(() -> service.submit(1L, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("category");
    }

    @Test
    void submit_nullCategory_defaultsToGeneral() {
        var req = new SubmitFeedbackRequest(null, null, "subject", "body", null);
        when(userDirectory.find(anyLong())).thenReturn(Optional.empty());
        when(feedbackRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.submit(1L, req);

        ArgumentCaptor<Feedback> cap = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepo).save(cap.capture());
        assertThat(cap.getValue().getCategory()).isEqualTo(FeedbackCategory.GENERAL);
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @Test
    void list_delegatesToRepositoryWithNormalizedFilters() {
        when(feedbackRepo.findAllFiltered(eq(FeedbackStatus.NEW), eq(FeedbackCategory.BUG_REPORT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        FeedbackPage result = service.list(1, 10, "new", "bug_report");

        assertThat(result.data()).isEmpty();
        verify(feedbackRepo).findAllFiltered(eq(FeedbackStatus.NEW), eq(FeedbackCategory.BUG_REPORT), any(Pageable.class));
    }

    @Test
    void list_blankFilters_passesNullToRepository() {
        when(feedbackRepo.findAllFiltered(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(1, 20, "", "");

        verify(feedbackRepo).findAllFiltered(isNull(), isNull(), any(Pageable.class));
    }

    // ── updateStatus ─────────────────────────────────────────────────────────

    @Test
    void updateStatus_toResolved_setsResolvedAt() {
        Feedback fb = stubFeedback(7L, FeedbackStatus.NEW);
        when(feedbackRepo.findById(7L)).thenReturn(Optional.of(fb));
        when(feedbackRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userDirectory.find(anyLong())).thenReturn(Optional.empty());

        service.updateStatus(7L, new UpdateStatusRequest("RESOLVED", "handled"));

        assertThat(fb.getStatus()).isEqualTo(FeedbackStatus.RESOLVED);
        assertThat(fb.getResolvedAt()).isNotNull();
        assertThat(fb.getAdminNote()).isEqualTo("handled");
    }

    @Test
    void updateStatus_toRead_doesNotSetResolvedAt() {
        Feedback fb = stubFeedback(8L, FeedbackStatus.NEW);
        when(feedbackRepo.findById(8L)).thenReturn(Optional.of(fb));
        when(feedbackRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userDirectory.find(anyLong())).thenReturn(Optional.empty());

        service.updateStatus(8L, new UpdateStatusRequest("READ", null));

        assertThat(fb.getStatus()).isEqualTo(FeedbackStatus.READ);
        assertThat(fb.getResolvedAt()).isNull();
    }

    @Test
    void updateStatus_notFound_throws() {
        when(feedbackRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(99L, new UpdateStatusRequest("READ", null)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void updateStatus_invalidStatus_throws() {
        Feedback fb = stubFeedback(1L, FeedbackStatus.NEW);
        when(feedbackRepo.findById(1L)).thenReturn(Optional.of(fb));

        assertThatThrownBy(() -> service.updateStatus(1L, new UpdateStatusRequest("DELETED", null)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Invalid status");
    }

    // ── summary ──────────────────────────────────────────────────────────────

    @Test
    void summary_returnsAggregatedCounts() {
        when(feedbackRepo.count()).thenReturn(10L);
        when(feedbackRepo.countByStatus(FeedbackStatus.NEW)).thenReturn(3L);
        when(feedbackRepo.countByStatus(FeedbackStatus.READ)).thenReturn(5L);
        when(feedbackRepo.countByStatus(FeedbackStatus.RESOLVED)).thenReturn(2L);
        Feedback rated = stubFeedback(1L, FeedbackStatus.RESOLVED);
        rated.setRating(4);
        when(feedbackRepo.findAll()).thenReturn(List.of(rated, stubFeedback(2L, FeedbackStatus.NEW)));

        FeedbackSummary s = service.summary();

        assertThat(s.total()).isEqualTo(10);
        assertThat(s.newCount()).isEqualTo(3);
        assertThat(s.readCount()).isEqualTo(5);
        assertThat(s.resolvedCount()).isEqualTo(2);
        assertThat(s.avgRating()).isEqualTo(4.0);
    }

    @Test
    void summary_noRatings_avgRatingIsNull() {
        when(feedbackRepo.count()).thenReturn(1L);
        when(feedbackRepo.countByStatus(any())).thenReturn(0L);
        when(feedbackRepo.findAll()).thenReturn(List.of(stubFeedback(1L, FeedbackStatus.NEW)));

        FeedbackSummary s = service.summary();

        assertThat(s.avgRating()).isNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Feedback stubFeedback(Long id, FeedbackStatus status) {
        Feedback f = new Feedback();
        f.setId(id);
        f.setUserId(1L);
        f.setCategory(FeedbackCategory.GENERAL);
        f.setSubject("subject");
        f.setBody("body");
        f.setStatus(status);
        f.setCreatedAt(Instant.now());
        return f;
    }
}
