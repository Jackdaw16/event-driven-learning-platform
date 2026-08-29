package io.github.jackdaw16.learningplatform.enrollment.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentTest {

    @Test
    void startsPendingPaymentWithZeroProgressAndNoCompletion() {
        Enrollment enrollment = new Enrollment(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-08-29T12:00:00Z")
        );

        assertEquals(EnrollmentStatus.PENDING_PAYMENT, enrollment.status());
        assertEquals(0, enrollment.progress());
        assertNull(enrollment.completedAt());
    }

    @Test
    void requiresIdReferencesAndEnrollmentTimestamp() {
        Instant enrolledAt = Instant.parse("2026-08-29T12:00:00Z");

        assertThrows(NullPointerException.class, () -> new Enrollment(null, UUID.randomUUID(), UUID.randomUUID(), enrolledAt));
        assertThrows(NullPointerException.class, () -> new Enrollment(UUID.randomUUID(), null, UUID.randomUUID(), enrolledAt));
        assertThrows(NullPointerException.class, () -> new Enrollment(UUID.randomUUID(), UUID.randomUUID(), null, enrolledAt));
        assertThrows(NullPointerException.class, () -> new Enrollment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null));
    }

    @Test
    void activatesOnlyPendingPaymentEnrollments() {
        Enrollment activeEnrollment = new Enrollment(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-08-29T12:00:00Z")
        );
        activeEnrollment.activate();

        assertEquals(EnrollmentStatus.ACTIVE, activeEnrollment.status());
        assertThrows(IllegalStateException.class, activeEnrollment::activate);

        Enrollment completedEnrollment = new Enrollment(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-08-29T12:00:00Z")
        );
        completedEnrollment.activate();
        completedEnrollment.updateProgress(100, Instant.parse("2026-08-29T13:00:00Z"));

        assertThrows(IllegalStateException.class, completedEnrollment::activate);

        Enrollment cancelledEnrollment = new Enrollment(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-08-29T12:00:00Z")
        );
        cancelledEnrollment.cancel();

        assertThrows(IllegalStateException.class, cancelledEnrollment::activate);
    }

    @Test
    void updatesProgressOnlyWhileActiveAndCompletesAtomicallyAtOneHundredPercent() {
        Enrollment enrollment = new Enrollment(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-08-29T12:00:00Z")
        );

        assertThrows(IllegalStateException.class, () -> enrollment.updateProgress(10, null));

        enrollment.activate();

        assertThrows(IllegalArgumentException.class, () -> enrollment.updateProgress(-1, null));
        assertThrows(IllegalArgumentException.class, () -> enrollment.updateProgress(101, null));
        assertThrows(IllegalArgumentException.class, () -> enrollment.updateProgress(25, Instant.parse("2026-08-29T12:30:00Z")));

        enrollment.updateProgress(75, null);
        assertEquals(75, enrollment.progress());
        assertThrows(NullPointerException.class, () -> enrollment.updateProgress(100, null));
        assertEquals(EnrollmentStatus.ACTIVE, enrollment.status());
        assertEquals(75, enrollment.progress());
        assertNull(enrollment.completedAt());

        Instant completedAt = Instant.parse("2026-08-29T13:00:00Z");
        enrollment.updateProgress(100, completedAt);

        assertEquals(EnrollmentStatus.COMPLETED, enrollment.status());
        assertEquals(100, enrollment.progress());
        assertNotNull(enrollment.completedAt());
        assertEquals(completedAt, enrollment.completedAt());
        assertThrows(IllegalStateException.class, () -> enrollment.updateProgress(100, completedAt));

        Enrollment cancelledEnrollment = new Enrollment(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-08-29T12:00:00Z")
        );
        cancelledEnrollment.cancel();

        assertThrows(IllegalStateException.class, () -> cancelledEnrollment.updateProgress(10, null));
    }

    @Test
    void cancelsPendingPaymentAndActiveEnrollmentsOnly() {
        Enrollment pendingEnrollment = new Enrollment(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-08-29T12:00:00Z")
        );
        pendingEnrollment.cancel();

        assertEquals(EnrollmentStatus.CANCELLED, pendingEnrollment.status());
        assertThrows(IllegalStateException.class, pendingEnrollment::cancel);

        Enrollment activeEnrollment = new Enrollment(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-08-29T12:00:00Z")
        );
        activeEnrollment.activate();
        activeEnrollment.cancel();

        assertEquals(EnrollmentStatus.CANCELLED, activeEnrollment.status());

        Enrollment completedEnrollment = new Enrollment(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-08-29T12:00:00Z")
        );
        completedEnrollment.activate();
        completedEnrollment.updateProgress(100, Instant.parse("2026-08-29T13:00:00Z"));

        assertThrows(IllegalStateException.class, completedEnrollment::cancel);
    }
}
