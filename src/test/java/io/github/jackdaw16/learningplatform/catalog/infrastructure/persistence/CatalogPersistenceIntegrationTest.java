package io.github.jackdaw16.learningplatform.catalog.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jackdaw16.learningplatform.catalog.application.CourseSearchCriteria;
import io.github.jackdaw16.learningplatform.catalog.application.PageQuery;
import io.github.jackdaw16.learningplatform.catalog.application.PageResult;
import io.github.jackdaw16.learningplatform.catalog.application.SortDirection;
import io.github.jackdaw16.learningplatform.catalog.application.port.CategoryRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.InstructorRepository;
import io.github.jackdaw16.learningplatform.catalog.domain.Category;
import io.github.jackdaw16.learningplatform.catalog.domain.CategoryStatus;
import io.github.jackdaw16.learningplatform.catalog.domain.Course;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseLevel;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseStatus;
import io.github.jackdaw16.learningplatform.catalog.domain.Instructor;
import io.github.jackdaw16.learningplatform.shared.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class CatalogPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InstructorRepository instructorRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void clearCatalogData() {
        jdbcTemplate.execute("TRUNCATE TABLE courses, categories, instructors");
    }

    @Test
    void appliesV1AndV2AndValidatesJpaMappingsAtContextStartup() {
        assertEquals(
                List.of("1", "2"),
                jdbcTemplate.queryForList(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                        String.class
                )
        );
    }

    @Test
    void savesAndFindsActiveAndArchivedCategories() {
        Category active = new Category(UUID.randomUUID(), "Development", "Software development courses");
        Category archived = Category.rehydrate(UUID.randomUUID(), "Legacy", null, CategoryStatus.ARCHIVED);

        categoryRepository.save(active);
        categoryRepository.save(archived);

        Category foundActive = categoryRepository.findById(active.id()).orElseThrow();
        Category foundArchived = categoryRepository.findById(archived.id()).orElseThrow();

        assertEquals(active.id(), foundActive.id());
        assertEquals(active.name(), foundActive.name());
        assertEquals(active.description(), foundActive.description());
        assertEquals(CategoryStatus.ACTIVE, foundActive.status());
        assertEquals(archived.id(), foundArchived.id());
        assertEquals(archived.name(), foundArchived.name());
        assertEquals(archived.description(), foundArchived.description());
        assertEquals(CategoryStatus.ARCHIVED, foundArchived.status());
    }

    @Test
    void savesAndFindsInstructorProfile() {
        Instructor instructor = new Instructor(
                UUID.randomUUID(),
                "Ada Lovelace",
                "ada.lovelace@example.com",
                "Pioneer of computer programming"
        );

        instructorRepository.save(instructor);

        assertEquals(instructor, instructorRepository.findById(instructor.id()).orElseThrow());
    }

    @Test
    void savesAndFindsDraftPublishedAndOccupiedCourses() {
        UUID categoryId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        categoryRepository.save(new Category(categoryId, "Engineering", "Engineering courses"));
        instructorRepository.save(new Instructor(instructorId, "Grace Hopper", "grace.hopper@example.com", null));

        Course draft = new Course(
                UUID.randomUUID(),
                "Java Foundations",
                "An introduction to Java",
                12,
                CourseLevel.BEGINNER,
                new Money(new BigDecimal("19.990"), Currency.getInstance("USD")),
                20,
                categoryId,
                instructorId
        );
        Course published = new Course(
                UUID.randomUUID(),
                "Spring Fundamentals",
                "An introduction to Spring",
                16,
                CourseLevel.INTERMEDIATE,
                new Money(new BigDecimal("29.50"), Currency.getInstance("EUR")),
                25,
                categoryId,
                instructorId
        );
        published.publish();
        Course occupied = new Course(
                UUID.randomUUID(),
                "PostgreSQL Fundamentals",
                "An introduction to PostgreSQL",
                10,
                CourseLevel.ADVANCED,
                new Money(new BigDecimal("39.0400"), Currency.getInstance("USD")),
                30,
                categoryId,
                instructorId
        );
        occupied.publish();
        occupied.reserveSeat();
        occupied.reserveSeat();

        courseRepository.save(draft);
        courseRepository.save(published);
        courseRepository.save(occupied);

        assertCourseRoundTrip(draft);
        assertCourseRoundTrip(published);
        assertCourseRoundTrip(occupied);
    }

    @Test
    void searchesCoursesWithCombinableDatabaseFiltersAndLiteralTitleMatching() {
        Currency usd = Currency.getInstance("USD");
        Currency eur = Currency.getInstance("EUR");
        Category software = saveCategory("Software");
        Category data = saveCategory("Data");
        Instructor instructor = saveInstructor("Ada Lovelace", "ada.search@example.com");
        Course matching = saveCourse(
                UUID.randomUUID(), "Spring Mastery", CourseLevel.ADVANCED, new BigDecimal("75.2500"), usd,
                2, 1, software.id(), instructor.id()
        );
        Course wrongCategory = saveCourse(
                UUID.randomUUID(), "Spring Mastery", CourseLevel.ADVANCED, new BigDecimal("75.25"), usd,
                2, 0, data.id(), instructor.id()
        );
        Course wrongLevel = saveCourse(
                UUID.randomUUID(), "Spring Basics", CourseLevel.BEGINNER, new BigDecimal("10.00"), usd,
                2, 0, software.id(), instructor.id()
        );
        Course wrongCurrency = saveCourse(
                UUID.randomUUID(), "Spring Euro", CourseLevel.ADVANCED, new BigDecimal("75.25"), eur,
                2, 0, software.id(), instructor.id()
        );
        Course outOfRange = saveCourse(
                UUID.randomUUID(), "Spring Premium", CourseLevel.ADVANCED, new BigDecimal("150.00"), usd,
                2, 0, software.id(), instructor.id()
        );
        Course full = saveCourse(
                UUID.randomUUID(), "Spring Full", CourseLevel.ADVANCED, new BigDecimal("75.25"), usd,
                1, 1, software.id(), instructor.id()
        );
        Course percentLiteral = saveCourse(
                UUID.randomUUID(), "Percent% Course", CourseLevel.BEGINNER, new BigDecimal("20.00"), usd,
                2, 0, software.id(), instructor.id()
        );
        saveCourse(
                UUID.randomUUID(), "PercentZZ Course", CourseLevel.BEGINNER, new BigDecimal("20.00"), usd,
                2, 0, software.id(), instructor.id()
        );
        Course underscoreLiteral = saveCourse(
                UUID.randomUUID(), "Data_101", CourseLevel.BEGINNER, new BigDecimal("20.00"), usd,
                2, 0, software.id(), instructor.id()
        );
        saveCourse(
                UUID.randomUUID(), "DataA101", CourseLevel.BEGINNER, new BigDecimal("20.00"), usd,
                2, 0, software.id(), instructor.id()
        );

        PageQuery titleOrder = new PageQuery(0, 100, "title", SortDirection.ASC);
        PageResult<Course> categoryResults = courseRepository.search(
                new CourseSearchCriteria(software.id(), null, null, null, null, null, null), titleOrder
        );
        PageResult<Course> levelResults = courseRepository.search(
                new CourseSearchCriteria(null, CourseLevel.ADVANCED, null, null, null, null, null), titleOrder
        );
        PageResult<Course> priceResults = courseRepository.search(
                new CourseSearchCriteria(null, null, usd, new BigDecimal("70.00"), new BigDecimal("80.00"), null, null),
                titleOrder
        );
        PageResult<Course> currencyResults = courseRepository.search(
                new CourseSearchCriteria(null, null, usd, null, null, null, null), titleOrder
        );
        PageResult<Course> titleResults = courseRepository.search(
                new CourseSearchCriteria(null, null, null, null, null, "mAsTeRy", null), titleOrder
        );
        PageResult<Course> percentResults = courseRepository.search(
                new CourseSearchCriteria(null, null, null, null, null, "Percent%", null), titleOrder
        );
        PageResult<Course> underscoreResults = courseRepository.search(
                new CourseSearchCriteria(null, null, null, null, null, "Data_101", null), titleOrder
        );
        PageResult<Course> availableResults = courseRepository.search(
                new CourseSearchCriteria(null, null, null, null, null, null, true), titleOrder
        );
        PageResult<Course> combinedResults = courseRepository.search(
                new CourseSearchCriteria(
                        software.id(),
                        CourseLevel.ADVANCED,
                        usd,
                        new BigDecimal("70.00"),
                        new BigDecimal("80.00"),
                        "spring",
                        true
                ),
                titleOrder
        );

        assertTrue(categoryResults.content().stream().allMatch(course -> course.categoryId().equals(software.id())));
        assertTrue(categoryResults.content().stream().noneMatch(course -> course.id().equals(wrongCategory.id())));
        assertTrue(levelResults.content().stream().allMatch(course -> course.level() == CourseLevel.ADVANCED));
        assertTrue(levelResults.content().stream().noneMatch(course -> course.id().equals(wrongLevel.id())));
        assertTrue(priceResults.content().stream().allMatch(course ->
                course.price().currency().equals(usd)
                        && course.price().amount().compareTo(new BigDecimal("70.00")) >= 0
                        && course.price().amount().compareTo(new BigDecimal("80.00")) <= 0
        ));
        assertTrue(priceResults.content().stream().noneMatch(course -> course.id().equals(wrongCurrency.id())));
        assertTrue(priceResults.content().stream().noneMatch(course -> course.id().equals(outOfRange.id())));
        assertTrue(currencyResults.content().stream().allMatch(course -> course.price().currency().equals(usd)));
        assertTrue(currencyResults.content().stream().noneMatch(course -> course.id().equals(wrongCurrency.id())));
        assertEquals(2, titleResults.content().size());
        assertTrue(titleResults.content().stream().anyMatch(course -> course.id().equals(matching.id())));
        assertTrue(titleResults.content().stream().anyMatch(course -> course.id().equals(wrongCategory.id())));
        assertEquals(List.of(percentLiteral.id()), percentResults.content().stream().map(Course::id).toList());
        assertEquals(List.of(underscoreLiteral.id()), underscoreResults.content().stream().map(Course::id).toList());
        assertTrue(availableResults.content().stream().noneMatch(course -> course.id().equals(full.id())));
        assertEquals(List.of(matching.id()), combinedResults.content().stream().map(Course::id).toList());
        Course searchedCourse = combinedResults.content().getFirst();
        assertEquals(new BigDecimal("75.2500"), searchedCourse.price().amount());
        assertEquals(usd, searchedCourse.price().currency());
    }

    @Test
    void paginatesAndSortsCoursesWithDeterministicSecondaryIdOrdering() {
        Category category = saveCategory("Pagination");
        Instructor instructor = saveInstructor("Grace Hopper", "grace.pagination@example.com");
        Currency usd = Currency.getInstance("USD");
        saveCourse(uuid(1), "Alpha", CourseLevel.BEGINNER, new BigDecimal("10.00"), usd, 2, 0, category.id(), instructor.id());
        saveCourse(uuid(2), "Bravo", CourseLevel.BEGINNER, new BigDecimal("10.00"), usd, 2, 0, category.id(), instructor.id());
        saveCourse(uuid(3), "Charlie", CourseLevel.BEGINNER, new BigDecimal("10.00"), usd, 2, 0, category.id(), instructor.id());
        saveCourse(uuid(4), "Delta", CourseLevel.BEGINNER, new BigDecimal("10.00"), usd, 2, 0, category.id(), instructor.id());
        Course firstSameTitle = saveCourse(
                uuid(10), "Same Title", CourseLevel.BEGINNER, new BigDecimal("10.00"), usd,
                2, 0, category.id(), instructor.id()
        );
        Course secondSameTitle = saveCourse(
                uuid(20), "Same Title", CourseLevel.BEGINNER, new BigDecimal("10.00"), usd,
                2, 0, category.id(), instructor.id()
        );

        PageResult<Course> secondPage = courseRepository.search(
                allCourses(), new PageQuery(1, 2, "title", SortDirection.ASC)
        );
        PageResult<Course> ascending = courseRepository.search(
                allCourses(), new PageQuery(0, 100, "title", SortDirection.ASC)
        );
        PageResult<Course> descending = courseRepository.search(
                allCourses(), new PageQuery(0, 100, "title", SortDirection.DESC)
        );

        assertEquals(List.of("Charlie", "Delta"), secondPage.content().stream().map(Course::title).toList());
        assertEquals(1, secondPage.page());
        assertEquals(2, secondPage.size());
        assertEquals(6, secondPage.totalElements());
        assertEquals(3, secondPage.totalPages());
        assertEquals(
                List.of("Alpha", "Bravo", "Charlie", "Delta", "Same Title", "Same Title"),
                ascending.content().stream().map(Course::title).toList()
        );
        assertEquals(
                List.of("Same Title", "Same Title", "Delta", "Charlie", "Bravo", "Alpha"),
                descending.content().stream().map(Course::title).toList()
        );
        assertEquals(
                List.of(firstSameTitle.id(), secondSameTitle.id()),
                ascending.content().stream()
                        .filter(course -> course.title().equals("Same Title"))
                        .map(Course::id)
                        .toList()
        );
    }

    @Test
    void listsCategoriesAndInstructorsWithPagination() {
        categoryRepository.save(new Category(uuid(1), "Alpha", null));
        categoryRepository.save(new Category(uuid(2), "Bravo", null));
        categoryRepository.save(new Category(uuid(3), "Charlie", null));
        instructorRepository.save(new Instructor(uuid(4), "Ada", "ada@example.com", null));
        instructorRepository.save(new Instructor(uuid(5), "Grace", "grace@example.com", null));
        instructorRepository.save(new Instructor(uuid(6), "Linus", "linus@example.com", null));

        PageResult<Category> categoryPage = categoryRepository.list(new PageQuery(1, 2, "name", SortDirection.ASC));
        PageResult<Instructor> instructorPage = instructorRepository.list(new PageQuery(0, 2, "email", SortDirection.DESC));

        assertEquals(List.of("Charlie"), categoryPage.content().stream().map(Category::name).toList());
        assertEquals(1, categoryPage.page());
        assertEquals(2, categoryPage.size());
        assertEquals(3, categoryPage.totalElements());
        assertEquals(2, categoryPage.totalPages());
        assertEquals(List.of("linus@example.com", "grace@example.com"), instructorPage.content().stream().map(Instructor::email).toList());
        assertEquals(0, instructorPage.page());
        assertEquals(2, instructorPage.size());
        assertEquals(3, instructorPage.totalElements());
        assertEquals(2, instructorPage.totalPages());
    }

    @Test
    void deletesUnreferencedCategoryInstructorAndCourse() {
        Category categoryToDelete = new Category(UUID.randomUUID(), "Design", "Design courses");
        Instructor instructorToDelete = new Instructor(
                UUID.randomUUID(),
                "Barbara Liskov",
                "barbara.liskov@example.com",
                null
        );
        categoryRepository.save(categoryToDelete);
        instructorRepository.save(instructorToDelete);

        UUID courseCategoryId = UUID.randomUUID();
        UUID courseInstructorId = UUID.randomUUID();
        categoryRepository.save(new Category(courseCategoryId, "Data", "Data courses"));
        instructorRepository.save(new Instructor(courseInstructorId, "Donald Knuth", "donald.knuth@example.com", null));
        Course courseToDelete = new Course(
                UUID.randomUUID(),
                "Algorithms",
                "Algorithm analysis",
                20,
                CourseLevel.ADVANCED,
                new Money(new BigDecimal("50.00"), Currency.getInstance("USD")),
                15,
                courseCategoryId,
                courseInstructorId
        );
        courseRepository.save(courseToDelete);

        categoryRepository.deleteById(categoryToDelete.id());
        instructorRepository.deleteById(instructorToDelete.id());
        courseRepository.deleteById(courseToDelete.id());

        assertTrue(categoryRepository.findById(categoryToDelete.id()).isEmpty());
        assertTrue(instructorRepository.findById(instructorToDelete.id()).isEmpty());
        assertTrue(courseRepository.findById(courseToDelete.id()).isEmpty());
    }

    private void assertCourseRoundTrip(Course expected) {
        Course actual = courseRepository.findById(expected.id()).orElseThrow();

        assertEquals(expected.id(), actual.id());
        assertEquals(expected.title(), actual.title());
        assertEquals(expected.description(), actual.description());
        assertEquals(expected.estimatedDurationHours(), actual.estimatedDurationHours());
        assertEquals(expected.level(), actual.level());
        assertEquals(expected.price().amount(), actual.price().amount());
        assertEquals(expected.price().currency(), actual.price().currency());
        assertEquals(expected.maximumSeats(), actual.maximumSeats());
        assertEquals(expected.occupiedSeats(), actual.occupiedSeats());
        assertEquals(expected.status(), actual.status());
        assertEquals(expected.categoryId(), actual.categoryId());
        assertEquals(expected.instructorId(), actual.instructorId());
    }

    private Category saveCategory(String name) {
        Category category = new Category(UUID.randomUUID(), name, null);
        categoryRepository.save(category);
        return category;
    }

    private Instructor saveInstructor(String name, String email) {
        Instructor instructor = new Instructor(UUID.randomUUID(), name, email, null);
        instructorRepository.save(instructor);
        return instructor;
    }

    private Course saveCourse(
            UUID id,
            String title,
            CourseLevel level,
            BigDecimal amount,
            Currency currency,
            int maximumSeats,
            int occupiedSeats,
            UUID categoryId,
            UUID instructorId
    ) {
        Course course = new Course(
                id,
                title,
                null,
                12,
                level,
                new Money(amount, currency),
                maximumSeats,
                categoryId,
                instructorId
        );
        if (occupiedSeats > 0) {
            course.publish();
            for (int seat = 0; seat < occupiedSeats; seat++) {
                course.reserveSeat();
            }
        }
        courseRepository.save(course);
        return course;
    }

    private CourseSearchCriteria allCourses() {
        return new CourseSearchCriteria(null, null, null, null, null, null, null);
    }

    private UUID uuid(long value) {
        return new UUID(0, value);
    }
}
