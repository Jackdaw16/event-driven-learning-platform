package io.github.jackdaw16.learningplatform.student.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudentTest {

    @Test
    void rejectsMissingIdBlankNamesOrEmailAndMissingRegistrationTimestamp() {
        Instant registrationTimestamp = Instant.parse("2026-08-29T12:00:00Z");

        assertThrows(NullPointerException.class, () -> new Student(null, "Ada", "Lovelace", "ada@example.com", registrationTimestamp));
        assertThrows(IllegalArgumentException.class, () -> new Student(UUID.randomUUID(), " ", "Lovelace", "ada@example.com", registrationTimestamp));
        assertThrows(IllegalArgumentException.class, () -> new Student(UUID.randomUUID(), "Ada", " ", "ada@example.com", registrationTimestamp));
        assertThrows(IllegalArgumentException.class, () -> new Student(UUID.randomUUID(), "Ada", "Lovelace", " ", registrationTimestamp));
        assertThrows(NullPointerException.class, () -> new Student(UUID.randomUUID(), "Ada", "Lovelace", "ada@example.com", null));
    }
}
