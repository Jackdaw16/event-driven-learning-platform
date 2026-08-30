package io.github.jackdaw16.learningplatform.enrollment.application;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.enrollment.application.port.EnrollmentRepository;
import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import io.github.jackdaw16.learningplatform.messaging.EnrollmentCompletedEventV1;
import io.github.jackdaw16.learningplatform.messaging.EventMetadataV1;
import io.github.jackdaw16.learningplatform.messaging.RabbitTopology;
import io.github.jackdaw16.learningplatform.messaging.application.port.IntegrationEventRecorder;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentProgressService {

    private final EnrollmentRepository enrollmentRepository;
    private final IntegrationEventRecorder integrationEventRecorder;
    private final Clock clock;

    public EnrollmentProgressService(
            EnrollmentRepository enrollmentRepository,
            IntegrationEventRecorder integrationEventRecorder,
            Clock clock
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.integrationEventRecorder = integrationEventRecorder;
        this.clock = clock;
    }

    @Transactional
    public Enrollment updateProgress(UUID enrollmentId, int progress) {
        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));

        if (enrollment.status() == EnrollmentStatus.COMPLETED) {
            if (progress == 100) {
                return enrollment;
            }
            throw new IllegalStateException("Only completed progress can be reported for a completed enrollment");
        }
        if (enrollment.status() != EnrollmentStatus.ACTIVE) {
            throw new IllegalStateException("Only active enrollments can update progress");
        }

        Instant completedAt = progress == 100 ? clock.instant().truncatedTo(ChronoUnit.MICROS) : null;
        enrollment.updateProgress(progress, completedAt);
        enrollmentRepository.save(enrollment);
        if (progress == 100) {
            integrationEventRecorder.record(new EnrollmentCompletedEventV1(
                    new EventMetadataV1(
                            UUID.randomUUID(),
                            EnrollmentCompletedEventV1.EVENT_TYPE,
                            EnrollmentCompletedEventV1.VERSION,
                            completedAt
                    ),
                    enrollment.id(),
                    enrollment.studentId(),
                    enrollment.courseId()
            ), RabbitTopology.ENROLLMENT_COMPLETED_ROUTING_KEY);
        }
        return enrollment;
    }
}
