package io.github.jackdaw16.learningplatform.enrollment.infrastructure.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.catalog.infrastructure.http.ApiExceptionHandler;
import io.github.jackdaw16.learningplatform.enrollment.application.CreateEnrollmentCommand;
import io.github.jackdaw16.learningplatform.enrollment.application.CreateEnrollmentResult;
import io.github.jackdaw16.learningplatform.enrollment.application.EnrollmentCancellationService;
import io.github.jackdaw16.learningplatform.enrollment.application.EnrollmentCreationService;
import io.github.jackdaw16.learningplatform.enrollment.application.exception.CourseSeatUnavailableException;
import io.github.jackdaw16.learningplatform.enrollment.application.exception.CourseSeatReleaseFailedException;
import io.github.jackdaw16.learningplatform.enrollment.application.exception.EnrollmentAlreadyExistsException;
import io.github.jackdaw16.learningplatform.enrollment.application.exception.EnrollmentCancellationNotAllowedException;
import io.github.jackdaw16.learningplatform.enrollment.application.exception.IdempotencyConflictException;
import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import io.github.jackdaw16.learningplatform.payment.domain.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EnrollmentControllerTest {

    private final EnrollmentCreationService enrollmentCreationService = mock(EnrollmentCreationService.class);
    private final EnrollmentCancellationService enrollmentCancellationService = mock(EnrollmentCancellationService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new EnrollmentController(enrollmentCreationService, enrollmentCancellationService)
                )
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createsEnrollmentWithCreatedResponse() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        CreateEnrollmentResult result = result(studentId, courseId, false);
        when(enrollmentCreationService.create(any(CreateEnrollmentCommand.class))).thenReturn(result);

        mockMvc.perform(createRequest(studentId, courseId, "create-key"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrollmentId").value(result.enrollment().id().toString()))
                .andExpect(jsonPath("$.studentId").value(studentId.toString()))
                .andExpect(jsonPath("$.courseId").value(courseId.toString()))
                .andExpect(jsonPath("$.enrollmentStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.paymentId").value(result.payment().id().toString()))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"));

        ArgumentCaptor<CreateEnrollmentCommand> command = ArgumentCaptor.forClass(CreateEnrollmentCommand.class);
        verify(enrollmentCreationService).create(command.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                new CreateEnrollmentCommand(studentId, courseId, "create-key"), command.getValue()
        );
    }

    @Test
    void returnsOkForIdempotentReplay() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(enrollmentCreationService.create(any(CreateEnrollmentCommand.class))).thenReturn(result(studentId, courseId, true));

        mockMvc.perform(createRequest(studentId, courseId, "replay-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(studentId.toString()))
                .andExpect(jsonPath("$.courseId").value(courseId.toString()));
    }

    @Test
    void rejectsMissingBlankAndOversizedIdempotencyKeys() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        mockMvc.perform(post("/api/students/{studentId}/enrollments", studentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(courseId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(createRequest(studentId, courseId, " "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(createRequest(studentId, courseId, "x".repeat(256)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(enrollmentCreationService);
    }

    @Test
    void returnsNotFoundForMissingEnrollmentCreationResource() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(enrollmentCreationService.create(any(CreateEnrollmentCommand.class)))
                .thenThrow(new ResourceNotFoundException("Student", studentId));

        mockMvc.perform(createRequest(studentId, courseId, "missing-key"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void returnsConflictWhenCourseIsFull() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(enrollmentCreationService.create(any(CreateEnrollmentCommand.class)))
                .thenThrow(new CourseSeatUnavailableException(courseId));

        mockMvc.perform(createRequest(studentId, courseId, "full-key"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void returnsConflictForLiveEnrollment() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(enrollmentCreationService.create(any(CreateEnrollmentCommand.class)))
                .thenThrow(new EnrollmentAlreadyExistsException(studentId, courseId));

        mockMvc.perform(createRequest(studentId, courseId, "existing-key"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void returnsConflictForIdempotencyKeyPayloadMismatch() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(enrollmentCreationService.create(any(CreateEnrollmentCommand.class)))
                .thenThrow(new IdempotencyConflictException("conflict-key"));

        mockMvc.perform(createRequest(studentId, courseId, "conflict-key"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void cancelsEnrollmentAndReturnsOkForRepeat() throws Exception {
        UUID enrollmentId = UUID.randomUUID();
        Enrollment cancelled = cancelledEnrollment(enrollmentId);
        when(enrollmentCancellationService.cancel(enrollmentId)).thenReturn(cancelled);

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", enrollmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollmentId").value(enrollmentId.toString()))
                .andExpect(jsonPath("$.studentId").value(cancelled.studentId().toString()))
                .andExpect(jsonPath("$.courseId").value(cancelled.courseId().toString()))
                .andExpect(jsonPath("$.enrollmentStatus").value("CANCELLED"));
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", enrollmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollmentStatus").value("CANCELLED"));

        verify(enrollmentCancellationService, times(2)).cancel(enrollmentId);
    }

    @Test
    void returnsNotFoundForMissingEnrollmentCancellation() throws Exception {
        UUID enrollmentId = UUID.randomUUID();
        when(enrollmentCancellationService.cancel(enrollmentId))
                .thenThrow(new ResourceNotFoundException("Enrollment", enrollmentId));

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", enrollmentId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void returnsConflictForCompletedEnrollmentCancellation() throws Exception {
        UUID enrollmentId = UUID.randomUUID();
        when(enrollmentCancellationService.cancel(enrollmentId))
                .thenThrow(new EnrollmentCancellationNotAllowedException(enrollmentId));

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", enrollmentId))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void returnsInternalServerErrorWhenCourseSeatReleaseFailsDuringCancellation() throws Exception {
        UUID enrollmentId = UUID.randomUUID();
        when(enrollmentCancellationService.cancel(enrollmentId))
                .thenThrow(new CourseSeatReleaseFailedException(UUID.randomUUID()));

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", enrollmentId))
                .andExpect(status().isInternalServerError());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createRequest(
            UUID studentId,
            UUID courseId,
            String idempotencyKey
    ) {
        return post("/api/students/{studentId}/enrollments", studentId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(courseId));
    }

    private String requestBody(UUID courseId) {
        return "{\"courseId\":\"%s\"}".formatted(courseId);
    }

    private CreateEnrollmentResult result(UUID studentId, UUID courseId, boolean replayed) {
        Enrollment enrollment = new Enrollment(UUID.randomUUID(), studentId, courseId, Instant.parse("2026-08-30T12:00:00Z"));
        Payment payment = new Payment(
                UUID.randomUUID(),
                enrollment.id(),
                new io.github.jackdaw16.learningplatform.shared.Money(new BigDecimal("19.99"), Currency.getInstance("USD")),
                "payment-key",
                Instant.parse("2026-08-30T12:00:00Z")
        );
        return new CreateEnrollmentResult(enrollment, payment, replayed);
    }

    private Enrollment cancelledEnrollment(UUID enrollmentId) {
        return Enrollment.rehydrate(
                enrollmentId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-08-30T12:00:00Z"),
                EnrollmentStatus.CANCELLED,
                0,
                null
        );
    }
}
