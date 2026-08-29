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
}
