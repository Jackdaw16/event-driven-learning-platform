package io.github.jackdaw16.learningplatform.catalog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.jackdaw16.learningplatform.shared.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseTest {

    @Test
    void startsAsDraftAndRequiresPublicationBeforeReservingSeats() {
        Course course = draftCourse(2);

        assertEquals(CourseStatus.DRAFT, course.status());
        assertThrows(CourseNotPublishedException.class, course::reserveSeat);

        course.publish();
        course.reserveSeat();

        assertEquals(1, course.occupiedSeats());
    }

    @Test
    void publishesOnlyDraftCoursesAndCannotBeRepublishedAfterArchival() {
        Course course = draftCourse(2);

        course.publish();
        assertThrows(IllegalStateException.class, course::publish);

        course.archive();

        assertEquals(CourseStatus.ARCHIVED, course.status());
        assertThrows(IllegalStateException.class, course::publish);
    }

    @Test
    void rejectsNonPositiveMaximumSeats() {
        assertThrows(IllegalArgumentException.class, () -> draftCourse(0));
        assertThrows(IllegalArgumentException.class, () -> draftCourse(-1));
    }

    @Test
    void rejectsBlankTitlesAndNonPositiveEstimatedDurationHours() {
        assertThrows(IllegalArgumentException.class, () -> draftCourse(" ", 12, 1));
        assertThrows(IllegalArgumentException.class, () -> draftCourse("Java 21 Fundamentals", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> draftCourse("Java 21 Fundamentals", -1, 1));
    }

    @Test
    void preventsReservingMoreSeatsThanTheMaximum() {
        Course course = draftCourse(2);
        course.publish();

        course.reserveSeat();
        course.reserveSeat();

        assertEquals(2, course.occupiedSeats());
        assertThrows(CourseFullException.class, course::reserveSeat);
        assertEquals(2, course.occupiedSeats());
    }

    @Test
    void releasesReservedSeatsWithoutAllowingTheCountToBecomeNegative() {
        Course course = draftCourse(1);
        course.publish();

        assertThrows(CourseHasNoOccupiedSeatsException.class, course::releaseSeat);

        course.reserveSeat();
        course.releaseSeat();

        assertEquals(0, course.occupiedSeats());
        assertThrows(CourseHasNoOccupiedSeatsException.class, course::releaseSeat);
    }

    @Test
    void rehydratesDraftPublishedAndArchivedCoursesWithTheirPersistedState() {
        Course draft = rehydrateCourse(CourseStatus.DRAFT, 0, 3);
        Course published = rehydrateCourse(CourseStatus.PUBLISHED, 2, 3);
        Course archived = rehydrateCourse(CourseStatus.ARCHIVED, 3, 3);

        assertEquals(CourseStatus.DRAFT, draft.status());
        assertEquals(0, draft.occupiedSeats());
        assertEquals(CourseStatus.PUBLISHED, published.status());
        assertEquals(2, published.occupiedSeats());
        assertEquals(CourseStatus.ARCHIVED, archived.status());
        assertEquals(3, archived.occupiedSeats());
    }

    @Test
    void rejectsInvalidOccupancyDuringRehydration() {
        assertThrows(IllegalArgumentException.class, () -> rehydrateCourse(CourseStatus.PUBLISHED, -1, 3));
        assertThrows(IllegalArgumentException.class, () -> rehydrateCourse(CourseStatus.PUBLISHED, 4, 3));
        assertThrows(IllegalArgumentException.class, () -> rehydrateCourse(CourseStatus.DRAFT, 1, 3));
        assertThrows(NullPointerException.class, () -> rehydrateCourse(null, 0, 3));
    }

    @Test
    void revisesDraftCoursesWithoutChangingIdentityStatusOrOccupancy() {
        Course course = draftCourse(3);
        UUID id = course.id();

        course.revise(
                "Advanced Java 21",
                "A deeper Java 21 course",
                24,
                CourseLevel.ADVANCED,
                new Money(new BigDecimal("99.99"), Currency.getInstance("USD")),
                5,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        assertEquals(id, course.id());
        assertEquals(CourseStatus.DRAFT, course.status());
        assertEquals(0, course.occupiedSeats());
        assertEquals("Advanced Java 21", course.title());
        assertEquals(5, course.maximumSeats());
    }

    @Test
    void revisesPublishedCoursesWithoutChangingTheirOccupancy() {
        Course course = draftCourse(3);
        course.publish();
        course.reserveSeat();

        course.revise(
                "Advanced Java 21",
                "A deeper Java 21 course",
                24,
                CourseLevel.ADVANCED,
                new Money(new BigDecimal("99.99"), Currency.getInstance("USD")),
                5,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        assertEquals(CourseStatus.PUBLISHED, course.status());
        assertEquals(1, course.occupiedSeats());
    }

    @Test
    void rejectsRevisionOfArchivedCourses() {
        Course course = draftCourse(3);
        course.archive();

        assertThrows(
                IllegalStateException.class,
                () -> course.revise(
                        "Advanced Java 21",
                        "A deeper Java 21 course",
                        24,
                        CourseLevel.ADVANCED,
                        new Money(new BigDecimal("99.99"), Currency.getInstance("USD")),
                        5,
                        UUID.randomUUID(),
                        UUID.randomUUID()
                )
        );
    }

    @Test
    void preventsRevisionFromReducingCapacityBelowOccupiedSeats() {
        Course course = draftCourse(3);
        course.publish();
        course.reserveSeat();
        course.reserveSeat();

        assertThrows(
                IllegalArgumentException.class,
                () -> course.revise(
                        "Advanced Java 21",
                        "A deeper Java 21 course",
                        24,
                        CourseLevel.ADVANCED,
                        new Money(new BigDecimal("99.99"), Currency.getInstance("USD")),
                        1,
                        UUID.randomUUID(),
                        UUID.randomUUID()
                )
        );
        assertEquals(3, course.maximumSeats());
        assertEquals(2, course.occupiedSeats());
    }

    private Course draftCourse(int maximumSeats) {
        return draftCourse("Java 21 Fundamentals", 12, maximumSeats);
    }

    private Course draftCourse(String title, int estimatedDurationHours, int maximumSeats) {
        return new Course(
                UUID.randomUUID(),
                title,
                "A practical introduction to Java 21",
                estimatedDurationHours,
                CourseLevel.BEGINNER,
                new Money(new BigDecimal("49.99"), Currency.getInstance("USD")),
                maximumSeats,
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }

    private Course rehydrateCourse(CourseStatus status, int occupiedSeats, int maximumSeats) {
        return Course.rehydrate(
                UUID.randomUUID(),
                "Java 21 Fundamentals",
                "A practical introduction to Java 21",
                12,
                CourseLevel.BEGINNER,
                new Money(new BigDecimal("49.99"), Currency.getInstance("USD")),
                maximumSeats,
                occupiedSeats,
                status,
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }
}
