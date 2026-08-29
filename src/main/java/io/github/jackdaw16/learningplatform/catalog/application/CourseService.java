package io.github.jackdaw16.learningplatform.catalog.application;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ConflictException;
import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.catalog.application.port.CategoryRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.InstructorRepository;
import io.github.jackdaw16.learningplatform.catalog.domain.Course;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final InstructorRepository instructorRepository;

    public CourseService(
            CourseRepository courseRepository,
            CategoryRepository categoryRepository,
            InstructorRepository instructorRepository
    ) {
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.instructorRepository = instructorRepository;
    }

    @Transactional
    public Course create(CreateCourseCommand command) {
        requireCategory(command.categoryId());
        requireInstructor(command.instructorId());

        Course course = new Course(
                UUID.randomUUID(),
                command.title(),
                command.description(),
                command.estimatedDurationHours(),
                command.level(),
                command.price(),
                command.maximumSeats(),
                command.categoryId(),
                command.instructorId()
        );
        courseRepository.save(course);
        return course;
    }

    @Transactional(readOnly = true)
    public Course getById(UUID id) {
        return findCourse(id);
    }

    @Transactional(readOnly = true)
    public PageResult<Course> search(CourseSearchCriteria criteria, PageQuery pageQuery) {
        validateSort(criteria, pageQuery);
        return courseRepository.search(criteria, pageQuery);
    }

    @Transactional
    public Course update(UUID id, UpdateCourseCommand command) {
        Course course = findCourse(id);
        requireCategory(command.categoryId());
        requireInstructor(command.instructorId());

        course.revise(
                command.title(),
                command.description(),
                command.estimatedDurationHours(),
                command.level(),
                command.price(),
                command.maximumSeats(),
                command.categoryId(),
                command.instructorId()
        );
        courseRepository.save(course);
        return course;
    }

    @Transactional
    public Course publish(UUID id) {
        Course course = findCourse(id);
        course.publish();
        courseRepository.save(course);
        return course;
    }

    @Transactional
    public Course archive(UUID id) {
        Course course = findCourse(id);
        course.archive();
        courseRepository.save(course);
        return course;
    }

    @Transactional
    public void delete(UUID id) {
        Course course = findCourse(id);
        if (course.status() != CourseStatus.DRAFT) {
            throw new ConflictException("Only draft courses can be deleted");
        }
        courseRepository.deleteById(id);
    }

    private Course findCourse(UUID id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }

    private void requireCategory(UUID id) {
        categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private void requireInstructor(UUID id) {
        instructorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor", id));
    }

    private void validateSort(CourseSearchCriteria criteria, PageQuery pageQuery) {
        switch (pageQuery.sortField()) {
            case "title", "price", "level", "estimatedDurationHours" -> {
            }
            default -> throw new IllegalArgumentException("Unsupported course sort field: " + pageQuery.sortField());
        }
        if ("price".equals(pageQuery.sortField()) && criteria.currency() == null) {
            throw new IllegalArgumentException("Currency is required when sorting courses by price");
        }
    }
}
