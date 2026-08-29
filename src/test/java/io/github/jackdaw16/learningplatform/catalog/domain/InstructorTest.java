package io.github.jackdaw16.learningplatform.catalog.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InstructorTest {

    @Test
    void rejectsMissingIdAndBlankNameOrEmail() {
        assertThrows(NullPointerException.class, () -> new Instructor(null, "Ada Lovelace", "ada@example.com", null));
        assertThrows(IllegalArgumentException.class, () -> new Instructor(UUID.randomUUID(), " ", "ada@example.com", null));
        assertThrows(IllegalArgumentException.class, () -> new Instructor(UUID.randomUUID(), "Ada Lovelace", " ", null));
    }
}
