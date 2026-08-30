package io.github.jackdaw16.learningplatform.enrollment.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.enrollment.application.CourseStudentEnrollment;
import io.github.jackdaw16.learningplatform.enrollment.application.StudentCourseEnrollment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface SpringDataEnrollmentRelationshipRepository extends Repository<EnrollmentJpaEntity, UUID> {

    @Query(
            value = """
                    SELECT new io.github.jackdaw16.learningplatform.enrollment.application.CourseStudentEnrollment(
                        student.id, student.firstName, student.lastName, student.email,
                        enrollment.id, enrollment.status, enrollment.progress, enrollment.enrolledAt
                    )
                    FROM EnrollmentJpaEntity enrollment
                    JOIN StudentJpaEntity student ON student.id = enrollment.studentId
                    WHERE enrollment.courseId = :courseId
                    ORDER BY enrollment.enrolledAt DESC, enrollment.id ASC
                    """,
            countQuery = """
                    SELECT count(enrollment)
                    FROM EnrollmentJpaEntity enrollment
                    WHERE enrollment.courseId = :courseId
                    """
    )
    Page<CourseStudentEnrollment> findStudentsByCourseId(@Param("courseId") UUID courseId, Pageable pageable);

    @Query(
            value = """
                    SELECT new io.github.jackdaw16.learningplatform.enrollment.application.StudentCourseEnrollment(
                        course.id, course.title, course.level, course.status,
                        enrollment.id, enrollment.status, enrollment.progress, enrollment.enrolledAt, enrollment.completedAt
                    )
                    FROM EnrollmentJpaEntity enrollment
                    JOIN CourseJpaEntity course ON course.id = enrollment.courseId
                    WHERE enrollment.studentId = :studentId
                    ORDER BY enrollment.enrolledAt DESC, enrollment.id ASC
                    """,
            countQuery = """
                    SELECT count(enrollment)
                    FROM EnrollmentJpaEntity enrollment
                    WHERE enrollment.studentId = :studentId
                    """
    )
    Page<StudentCourseEnrollment> findCoursesByStudentId(@Param("studentId") UUID studentId, Pageable pageable);
}
