package io.github.jackdaw16.learningplatform.enrollment.application;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseSeatInventory;
import io.github.jackdaw16.learningplatform.enrollment.application.exception.CourseSeatReleaseFailedException;
import io.github.jackdaw16.learningplatform.enrollment.application.port.EnrollmentRepository;
import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentCancellationService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseSeatInventory courseSeatInventory;

    public EnrollmentCancellationService(
            EnrollmentRepository enrollmentRepository,
            CourseSeatInventory courseSeatInventory
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseSeatInventory = courseSeatInventory;
    }

    @Transactional
    public Enrollment cancel(UUID enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));
        if (enrollment.status() == EnrollmentStatus.CANCELLED) {
            return enrollment;
        }

        enrollment.cancel();
        enrollmentRepository.save(enrollment);
        if (!courseSeatInventory.release(enrollment.courseId())) {
            throw new CourseSeatReleaseFailedException(enrollment.courseId());
        }
        return enrollment;
    }
}
