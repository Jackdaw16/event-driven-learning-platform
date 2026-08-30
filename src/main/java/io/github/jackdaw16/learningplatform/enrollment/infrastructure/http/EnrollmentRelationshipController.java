package io.github.jackdaw16.learningplatform.enrollment.infrastructure.http;

import io.github.jackdaw16.learningplatform.auth.infrastructure.security.OwnershipAuthorization;
import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.enrollment.application.EnrollmentRelationshipQueryService;
import io.github.jackdaw16.learningplatform.enrollment.application.RelationshipPageQuery;
import io.github.jackdaw16.learningplatform.student.application.port.StudentRepository;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EnrollmentRelationshipController {

    private final OwnershipAuthorization ownershipAuthorization;
    private final EnrollmentRelationshipQueryService enrollmentRelationshipQueryService;
    private final StudentRepository studentRepository;

    public EnrollmentRelationshipController(
            OwnershipAuthorization ownershipAuthorization,
            EnrollmentRelationshipQueryService enrollmentRelationshipQueryService,
            StudentRepository studentRepository
    ) {
        this.ownershipAuthorization = ownershipAuthorization;
        this.enrollmentRelationshipQueryService = enrollmentRelationshipQueryService;
        this.studentRepository = studentRepository;
    }

    @GetMapping("/api/courses/{courseId}/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public RelationshipPageResponse<CourseStudentEnrollmentResponse> listCourseStudents(
            @PathVariable UUID courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ownershipAuthorization.requireCourseStudentsRead(courseId);
        return RelationshipPageResponse.from(
                enrollmentRelationshipQueryService.findStudentsByCourseId(courseId, new RelationshipPageQuery(page, size)),
                CourseStudentEnrollmentResponse::from
        );
    }

    @GetMapping("/api/students/{studentId}/courses")
    @PreAuthorize("hasAnyRole('ADMIN', 'STUDENT')")
    public RelationshipPageResponse<StudentCourseEnrollmentResponse> listStudentCourses(
            @PathVariable UUID studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ownershipAuthorization.requireStudentCoursesRead(studentId);
        studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        return RelationshipPageResponse.from(
                enrollmentRelationshipQueryService.findCoursesByStudentId(studentId, new RelationshipPageQuery(page, size)),
                StudentCourseEnrollmentResponse::from
        );
    }
}
