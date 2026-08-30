package io.github.jackdaw16.learningplatform.enrollment.application.port;

import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository {

    void save(Enrollment enrollment);

    Optional<Enrollment> findById(UUID id);

    Optional<Enrollment> findLiveByStudentIdAndCourseId(UUID studentId, UUID courseId);
}
