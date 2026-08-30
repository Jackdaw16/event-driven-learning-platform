package io.github.jackdaw16.learningplatform.enrollment.infrastructure.http;

import io.github.jackdaw16.learningplatform.auth.infrastructure.security.OwnershipAuthorization;
import io.github.jackdaw16.learningplatform.enrollment.application.CreateEnrollmentCommand;
import io.github.jackdaw16.learningplatform.enrollment.application.CreateEnrollmentResult;
import io.github.jackdaw16.learningplatform.enrollment.application.EnrollmentCancellationService;
import io.github.jackdaw16.learningplatform.enrollment.application.EnrollmentCreationService;
import io.github.jackdaw16.learningplatform.enrollment.application.EnrollmentProgressService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EnrollmentController {

    private final EnrollmentCreationService enrollmentCreationService;
    private final EnrollmentCancellationService enrollmentCancellationService;
    private final EnrollmentProgressService enrollmentProgressService;
    private final OwnershipAuthorization ownershipAuthorization;

    public EnrollmentController(
            EnrollmentCreationService enrollmentCreationService,
            EnrollmentCancellationService enrollmentCancellationService,
            EnrollmentProgressService enrollmentProgressService,
            OwnershipAuthorization ownershipAuthorization
    ) {
        this.enrollmentCreationService = enrollmentCreationService;
        this.enrollmentCancellationService = enrollmentCancellationService;
        this.enrollmentProgressService = enrollmentProgressService;
        this.ownershipAuthorization = ownershipAuthorization;
    }

    @PostMapping("/api/students/{studentId}/enrollments")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<EnrollmentCreationResponse> create(
            @PathVariable UUID studentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody EnrollmentRequest request
    ) {
        ownershipAuthorization.requireStudentEnrollmentCreation(studentId);
        CreateEnrollmentResult result = enrollmentCreationService.create(
                new CreateEnrollmentCommand(studentId, request.courseId(), idempotencyKey)
        );
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(EnrollmentCreationResponse.from(result));
    }

    @PostMapping("/api/enrollments/{enrollmentId}/cancel")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public EnrollmentCancellationResponse cancel(@PathVariable UUID enrollmentId) {
        ownershipAuthorization.requireEnrollmentMutation(enrollmentId);
        return EnrollmentCancellationResponse.from(enrollmentCancellationService.cancel(enrollmentId));
    }

    @PatchMapping("/api/enrollments/{enrollmentId}/progress")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public EnrollmentProgressResponse updateProgress(
            @PathVariable UUID enrollmentId,
            @Valid @RequestBody EnrollmentProgressRequest request
    ) {
        ownershipAuthorization.requireEnrollmentMutation(enrollmentId);
        return EnrollmentProgressResponse.from(enrollmentProgressService.updateProgress(enrollmentId, request.progress()));
    }
}
