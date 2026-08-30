package io.github.jackdaw16.learningplatform.enrollment.application;

import io.github.jackdaw16.learningplatform.enrollment.application.port.EnrollmentRelationshipRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentRelationshipQueryService {

    private final EnrollmentRelationshipRepository enrollmentRelationshipRepository;

    public EnrollmentRelationshipQueryService(EnrollmentRelationshipRepository enrollmentRelationshipRepository) {
        this.enrollmentRelationshipRepository = enrollmentRelationshipRepository;
    }

    @Transactional(readOnly = true)
    public RelationshipPageResult<CourseStudentEnrollment> findStudentsByCourseId(
            UUID courseId,
            RelationshipPageQuery pageQuery
    ) {
        return enrollmentRelationshipRepository.findStudentsByCourseId(courseId, pageQuery);
    }

    @Transactional(readOnly = true)
    public RelationshipPageResult<StudentCourseEnrollment> findCoursesByStudentId(
            UUID studentId,
            RelationshipPageQuery pageQuery
    ) {
        return enrollmentRelationshipRepository.findCoursesByStudentId(studentId, pageQuery);
    }
}
