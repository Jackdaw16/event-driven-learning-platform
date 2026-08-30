package io.github.jackdaw16.learningplatform.auth.infrastructure.security;

import io.github.jackdaw16.learningplatform.auth.domain.AuthRole;
import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseRepository;
import io.github.jackdaw16.learningplatform.enrollment.application.port.EnrollmentRepository;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class OwnershipAuthorization {

    private final AuthenticatedActorProvider authenticatedActorProvider;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    public OwnershipAuthorization(
            AuthenticatedActorProvider authenticatedActorProvider,
            CourseRepository courseRepository,
            EnrollmentRepository enrollmentRepository
    ) {
        this.authenticatedActorProvider = authenticatedActorProvider;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    public void requireStudentEnrollmentCreation(UUID studentId) {
        AuthenticatedActor actor = authenticatedActorProvider.currentActor();
        if (actor.role() == AuthRole.ADMIN) {
            return;
        }
        requireRole(actor, AuthRole.STUDENT);
        requireActorId(actor, studentId);
    }

    public void requireEnrollmentMutation(UUID enrollmentId) {
        AuthenticatedActor actor = authenticatedActorProvider.currentActor();
        if (actor.role() == AuthRole.ADMIN) {
            return;
        }
        requireRole(actor, AuthRole.STUDENT);
        UUID enrollmentStudentId = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId))
                .studentId();
        requireActorId(actor, enrollmentStudentId);
    }

    public void requireCourseCreation(UUID instructorId) {
        AuthenticatedActor actor = authenticatedActorProvider.currentActor();
        if (actor.role() == AuthRole.ADMIN) {
            return;
        }
        requireRole(actor, AuthRole.INSTRUCTOR);
        requireActorId(actor, instructorId);
    }

    public void requireCourseMutation(UUID courseId) {
        AuthenticatedActor actor = authenticatedActorProvider.currentActor();
        if (actor.role() == AuthRole.ADMIN) {
            return;
        }
        requireRole(actor, AuthRole.INSTRUCTOR);
        UUID courseInstructorId = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId))
                .instructorId();
        requireActorId(actor, courseInstructorId);
    }

    public void requireCourseUpdate(UUID courseId, UUID requestedInstructorId) {
        AuthenticatedActor actor = authenticatedActorProvider.currentActor();
        if (actor.role() == AuthRole.ADMIN) {
            return;
        }
        requireRole(actor, AuthRole.INSTRUCTOR);
        UUID courseInstructorId = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId))
                .instructorId();
        requireActorId(actor, courseInstructorId);
        requireActorId(actor, requestedInstructorId);
    }

    public void requireCourseStudentsRead(UUID courseId) {
        AuthenticatedActor actor = authenticatedActorProvider.currentActor();
        if (actor.role() == AuthRole.STUDENT) {
            deny();
        }
        UUID courseInstructorId = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId))
                .instructorId();
        if (actor.role() == AuthRole.INSTRUCTOR) {
            requireActorId(actor, courseInstructorId);
        }
    }

    public void requireStudentCoursesRead(UUID studentId) {
        AuthenticatedActor actor = authenticatedActorProvider.currentActor();
        if (actor.role() == AuthRole.INSTRUCTOR) {
            deny();
        }
        if (actor.role() == AuthRole.STUDENT) {
            requireActorId(actor, studentId);
        }
    }

    private void requireRole(AuthenticatedActor actor, AuthRole expectedRole) {
        if (actor.role() != expectedRole) {
            deny();
        }
    }

    private void requireActorId(AuthenticatedActor actor, UUID resourceActorId) {
        if (actor.actorId() == null || !actor.actorId().equals(resourceActorId)) {
            deny();
        }
    }

    private void deny() {
        throw new AccessDeniedException("Actor does not own this resource");
    }
}
