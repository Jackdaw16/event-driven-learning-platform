package io.github.jackdaw16.learningplatform.certificate.application;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.certificate.application.port.CertificateRepository;
import io.github.jackdaw16.learningplatform.certificate.domain.Certificate;
import io.github.jackdaw16.learningplatform.enrollment.application.port.EnrollmentRepository;
import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import io.github.jackdaw16.learningplatform.messaging.EnrollmentCompletedEventV1;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CertificateIssuanceService {

    private final EnrollmentRepository enrollmentRepository;
    private final CertificateRepository certificateRepository;
    private final Clock clock;

    public CertificateIssuanceService(
            EnrollmentRepository enrollmentRepository,
            CertificateRepository certificateRepository,
            Clock clock
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.certificateRepository = certificateRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void issue(EnrollmentCompletedEventV1 event) {
        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(event.enrollmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", event.enrollmentId()));
        if (enrollment.status() != EnrollmentStatus.COMPLETED) {
            throw new IllegalStateException("only completed enrollments can receive certificates");
        }
        if (!enrollment.studentId().equals(event.studentId())) {
            throw new IllegalArgumentException("event student does not match the enrollment");
        }
        if (!enrollment.courseId().equals(event.courseId())) {
            throw new IllegalArgumentException("event course does not match the enrollment");
        }
        if (certificateRepository.findByEnrollmentId(enrollment.id()).isPresent()) {
            return;
        }

        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        certificateRepository.save(new Certificate(
                UUID.randomUUID(),
                enrollment.id(),
                UUID.randomUUID().toString(),
                issuedAt
        ));
    }
}
