package io.github.jackdaw16.learningplatform.enrollment.application;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseSeatInventory;
import io.github.jackdaw16.learningplatform.catalog.domain.Course;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseStatus;
import io.github.jackdaw16.learningplatform.enrollment.application.exception.CourseNotEnrollableException;
import io.github.jackdaw16.learningplatform.enrollment.application.exception.CourseSeatUnavailableException;
import io.github.jackdaw16.learningplatform.enrollment.application.port.EnrollmentRepository;
import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import io.github.jackdaw16.learningplatform.messaging.EnrollmentCreatedEventV1;
import io.github.jackdaw16.learningplatform.messaging.EventMetadataV1;
import io.github.jackdaw16.learningplatform.messaging.RabbitTopology;
import io.github.jackdaw16.learningplatform.messaging.application.port.IntegrationEventRecorder;
import io.github.jackdaw16.learningplatform.payment.application.port.PaymentRepository;
import io.github.jackdaw16.learningplatform.payment.domain.Payment;
import io.github.jackdaw16.learningplatform.student.application.port.StudentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EnrollmentCreationTransaction {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final CourseSeatInventory courseSeatInventory;
    private final EnrollmentRepository enrollmentRepository;
    private final PaymentRepository paymentRepository;
    private final IntegrationEventRecorder integrationEventRecorder;
    private final Clock clock;

    public EnrollmentCreationTransaction(
            StudentRepository studentRepository,
            CourseRepository courseRepository,
            CourseSeatInventory courseSeatInventory,
            EnrollmentRepository enrollmentRepository,
            PaymentRepository paymentRepository,
            IntegrationEventRecorder integrationEventRecorder,
            Clock clock
    ) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.courseSeatInventory = courseSeatInventory;
        this.enrollmentRepository = enrollmentRepository;
        this.paymentRepository = paymentRepository;
        this.integrationEventRecorder = integrationEventRecorder;
        this.clock = clock;
    }

    @Transactional
    public CreateEnrollmentResult create(CreateEnrollmentCommand command) {
        studentRepository.findById(command.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student", command.studentId()));
        Course course = courseRepository.findById(command.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", command.courseId()));

        if (!courseSeatInventory.reserve(course.id())) {
            throw classifyUnavailableCourse(course.id());
        }

        Instant now = clock.instant();
        Enrollment enrollment = new Enrollment(UUID.randomUUID(), command.studentId(), course.id(), now);
        Payment payment = new Payment(
                UUID.randomUUID(),
                enrollment.id(),
                course.price(),
                command.idempotencyKey(),
                now
        );
        enrollmentRepository.save(enrollment);
        paymentRepository.save(payment);
        integrationEventRecorder.record(new EnrollmentCreatedEventV1(
                new EventMetadataV1(
                        UUID.randomUUID(),
                        EnrollmentCreatedEventV1.EVENT_TYPE,
                        EnrollmentCreatedEventV1.VERSION,
                        now.truncatedTo(ChronoUnit.MICROS)
                ),
                enrollment.id(),
                payment.id(),
                payment.amount().amount(),
                payment.amount().currency().getCurrencyCode()
        ), RabbitTopology.ENROLLMENT_CREATED_ROUTING_KEY);
        return new CreateEnrollmentResult(enrollment, payment, false);
    }

    private RuntimeException classifyUnavailableCourse(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        if (course.status() != CourseStatus.PUBLISHED) {
            return new CourseNotEnrollableException(courseId);
        }
        return new CourseSeatUnavailableException(courseId);
    }
}
