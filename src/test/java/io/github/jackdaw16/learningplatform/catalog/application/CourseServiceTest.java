package io.github.jackdaw16.learningplatform.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ConflictException;
import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.catalog.application.port.CategoryRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.InstructorRepository;
import io.github.jackdaw16.learningplatform.catalog.domain.Category;
import io.github.jackdaw16.learningplatform.catalog.domain.Course;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseLevel;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseStatus;
import io.github.jackdaw16.learningplatform.catalog.domain.Instructor;
import io.github.jackdaw16.learningplatform.shared.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseServiceTest {

    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final InstructorRepository instructorRepository = mock(InstructorRepository.class);
    private final CourseService service = new CourseService(courseRepository, categoryRepository, instructorRepository);
    private final UUID categoryId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();

    @Test
    void rejectsCreationWhenCategoryIsMissing() {
        CreateCourseCommand command = createCommand();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.create(command));

        verifyNoInteractions(instructorRepository, courseRepository);
    }

    @Test
    void rejectsCreationWhenInstructorIsMissing() {
        CreateCourseCommand command = createCommand();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category()));
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.create(command));

        verify(courseRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createsDraftCourseAndSavesIt() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category()));
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(instructor()));

        Course created = service.create(createCommand());

        assertEquals(CourseStatus.DRAFT, created.status());
        assertEquals(0, created.occupiedSeats());
        assertEquals(categoryId, created.categoryId());
        assertEquals(instructorId, created.instructorId());
        verify(courseRepository).save(created);
    }

    @Test
    void permitsCreationWithArchivedCategory() {
        Category archivedCategory = category();
        archivedCategory.archive();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(archivedCategory));
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(instructor()));

        Course created = service.create(createCommand());

        assertEquals(CourseStatus.DRAFT, created.status());
        verify(courseRepository).save(created);
    }

    @Test
    void rejectsUpdateWhenCategoryIsMissing() {
        Course course = draftCourse();
        when(courseRepository.findById(course.id())).thenReturn(Optional.of(course));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(course.id(), updateCommand()));

        verifyNoInteractions(instructorRepository);
        verify(courseRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsUpdateWhenInstructorIsMissing() {
        Course course = draftCourse();
        when(courseRepository.findById(course.id())).thenReturn(Optional.of(course));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category()));
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(course.id(), updateCommand()));

        verify(courseRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void revisesCourseAfterBothReferencesExistWhilePreservingState() {
        Course course = draftCourse();
        course.publish();
        course.reserveSeat();
        when(courseRepository.findById(course.id())).thenReturn(Optional.of(course));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category()));
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(instructor()));

        Course updated = service.update(course.id(), updateCommand());

        assertSame(course, updated);
        assertEquals("Advanced Java", updated.title());
        assertEquals(5, updated.maximumSeats());
        assertEquals(CourseStatus.PUBLISHED, updated.status());
        assertEquals(1, updated.occupiedSeats());
        verify(courseRepository).save(course);
    }

    @Test
    void publishesDraftCourseThroughDomainTransition() {
        Course course = draftCourse();
        when(courseRepository.findById(course.id())).thenReturn(Optional.of(course));

        Course published = service.publish(course.id());

        assertEquals(CourseStatus.PUBLISHED, published.status());
        verify(courseRepository).save(course);
    }

    @Test
    void propagatesInvalidPublicationFromDomain() {
        Course course = draftCourse();
        course.publish();
        when(courseRepository.findById(course.id())).thenReturn(Optional.of(course));

        assertThrows(IllegalStateException.class, () -> service.publish(course.id()));

        verify(courseRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void archivesCourseThroughDomainTransition() {
        Course course = draftCourse();
        when(courseRepository.findById(course.id())).thenReturn(Optional.of(course));

        Course archived = service.archive(course.id());

        assertEquals(CourseStatus.ARCHIVED, archived.status());
        verify(courseRepository).save(course);
    }

    @Test
    void deletesDraftCoursePhysically() {
        Course course = draftCourse();
        when(courseRepository.findById(course.id())).thenReturn(Optional.of(course));

        service.delete(course.id());

        verify(courseRepository).deleteById(course.id());
    }

    @Test
    void rejectsDeletingPublishedOrArchivedCourses() {
        Course published = draftCourse();
        published.publish();
        Course archived = draftCourse();
        archived.archive();
        when(courseRepository.findById(published.id())).thenReturn(Optional.of(published));
        when(courseRepository.findById(archived.id())).thenReturn(Optional.of(archived));

        assertThrows(ConflictException.class, () -> service.delete(published.id()));
        assertThrows(ConflictException.class, () -> service.delete(archived.id()));

        verify(courseRepository, never()).deleteById(published.id());
        verify(courseRepository, never()).deleteById(archived.id());
    }

    @Test
    void reportsMissingCourse() {
        UUID id = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getById(id));
    }

    @Test
    void delegatesSearchToTheCourseRepository() {
        CourseSearchCriteria criteria = new CourseSearchCriteria(
                categoryId,
                CourseLevel.BEGINNER,
                Currency.getInstance("USD"),
                new BigDecimal("10.00"),
                new BigDecimal("50.00"),
                "java",
                true
        );
        PageQuery pageQuery = new PageQuery(1, 20, "title", SortDirection.DESC);
        PageResult<Course> expected = new PageResult<>(List.of(draftCourse()), 1, 20, 21, 2);
        when(courseRepository.search(criteria, pageQuery)).thenReturn(expected);

        PageResult<Course> actual = service.search(criteria, pageQuery);

        assertSame(expected, actual);
        verify(courseRepository).search(criteria, pageQuery);
    }

    @Test
    void rejectsPriceSortingWithoutCurrencyBeforeQuerying() {
        CourseSearchCriteria criteria = new CourseSearchCriteria(null, null, null, null, null, null, null);
        PageQuery pageQuery = new PageQuery(0, 20, "price", SortDirection.ASC);

        assertThrows(IllegalArgumentException.class, () -> service.search(criteria, pageQuery));

        verifyNoInteractions(courseRepository);
    }

    private CreateCourseCommand createCommand() {
        return new CreateCourseCommand(
                "Java Foundations",
                "An introduction to Java",
                12,
                CourseLevel.BEGINNER,
                money("49.99"),
                3,
                categoryId,
                instructorId
        );
    }

    private UpdateCourseCommand updateCommand() {
        return new UpdateCourseCommand(
                "Advanced Java",
                "A deeper Java course",
                24,
                CourseLevel.ADVANCED,
                money("99.99"),
                5,
                categoryId,
                instructorId
        );
    }

    private Category category() {
        return new Category(categoryId, "Programming", "Software development courses");
    }

    private Instructor instructor() {
        return new Instructor(instructorId, "Ada Lovelace", "ada@example.com", "Mathematician");
    }

    private Course draftCourse() {
        CreateCourseCommand command = createCommand();
        return new Course(
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
    }

    private Money money(String amount) {
        return new Money(new BigDecimal(amount), Currency.getInstance("USD"));
    }
}
