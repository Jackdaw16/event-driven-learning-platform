package io.github.jackdaw16.learningplatform.enrollment.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.enrollment.application.port.EnrollmentRepository;
import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentPersistenceAdapter implements EnrollmentRepository {

    private final SpringDataEnrollmentRepository repository;

    public EnrollmentPersistenceAdapter(SpringDataEnrollmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(Enrollment enrollment) {
        repository.save(new EnrollmentJpaEntity(
                enrollment.id(),
                enrollment.studentId(),
                enrollment.courseId(),
                enrollment.status(),
                enrollment.progress(),
                enrollment.enrolledAt(),
                enrollment.completedAt()
        ));
    }

    @Override
    public Optional<Enrollment> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Enrollment> findByIdForUpdate(UUID enrollmentId) {
        return repository.findByIdForUpdate(enrollmentId).map(this::toDomain);
    }

    @Override
    public Optional<Enrollment> findLiveByStudentIdAndCourseId(UUID studentId, UUID courseId) {
        return repository.findByStudentIdAndCourseIdAndStatusIn(
                studentId,
                courseId,
                List.of(EnrollmentStatus.PENDING_PAYMENT, EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED)
        ).map(this::toDomain);
    }

    private Enrollment toDomain(EnrollmentJpaEntity entity) {
        return Enrollment.rehydrate(
                entity.id(),
                entity.studentId(),
                entity.courseId(),
                entity.enrolledAt(),
                entity.status(),
                entity.progress(),
                entity.completedAt()
        );
    }
}
