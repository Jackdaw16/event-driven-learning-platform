package io.github.jackdaw16.learningplatform.enrollment.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.enrollment.application.CourseStudentEnrollment;
import io.github.jackdaw16.learningplatform.enrollment.application.RelationshipPageQuery;
import io.github.jackdaw16.learningplatform.enrollment.application.RelationshipPageResult;
import io.github.jackdaw16.learningplatform.enrollment.application.StudentCourseEnrollment;
import io.github.jackdaw16.learningplatform.enrollment.application.port.EnrollmentRelationshipRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentRelationshipPersistenceAdapter implements EnrollmentRelationshipRepository {

    private final SpringDataEnrollmentRelationshipRepository repository;

    public EnrollmentRelationshipPersistenceAdapter(SpringDataEnrollmentRelationshipRepository repository) {
        this.repository = repository;
    }

    @Override
    public RelationshipPageResult<CourseStudentEnrollment> findStudentsByCourseId(
            UUID courseId,
            RelationshipPageQuery pageQuery
    ) {
        return page(repository.findStudentsByCourseId(courseId, pageable(pageQuery)));
    }

    @Override
    public RelationshipPageResult<StudentCourseEnrollment> findCoursesByStudentId(
            UUID studentId,
            RelationshipPageQuery pageQuery
    ) {
        return page(repository.findCoursesByStudentId(studentId, pageable(pageQuery)));
    }

    private PageRequest pageable(RelationshipPageQuery pageQuery) {
        return PageRequest.of(pageQuery.page(), pageQuery.size());
    }

    private <T> RelationshipPageResult<T> page(Page<T> page) {
        return new RelationshipPageResult<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
