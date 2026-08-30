package io.github.jackdaw16.learningplatform.enrollment.application.port;

import io.github.jackdaw16.learningplatform.enrollment.application.CourseStudentEnrollment;
import io.github.jackdaw16.learningplatform.enrollment.application.RelationshipPageQuery;
import io.github.jackdaw16.learningplatform.enrollment.application.RelationshipPageResult;
import io.github.jackdaw16.learningplatform.enrollment.application.StudentCourseEnrollment;
import java.util.UUID;

public interface EnrollmentRelationshipRepository {

    RelationshipPageResult<CourseStudentEnrollment> findStudentsByCourseId(UUID courseId, RelationshipPageQuery pageQuery);

    RelationshipPageResult<StudentCourseEnrollment> findCoursesByStudentId(UUID studentId, RelationshipPageQuery pageQuery);
}
