package io.github.jackdaw16.learningplatform.catalog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void revisesProfileWithTheSameId() {
        UUID id = UUID.randomUUID();
        Instructor instructor = new Instructor(id, "Ada Lovelace", "ada@example.com", "Mathematician");

        Instructor revised = instructor.reviseProfile("Grace Hopper", "grace@example.com", "Computer scientist");

        assertEquals(id, revised.id());
        assertEquals("Grace Hopper", revised.name());
    }

    @Test
    void rejectsInvalidNameAndEmailDuringProfileRevision() {
        Instructor instructor = new Instructor(UUID.randomUUID(), "Ada Lovelace", "ada@example.com", "Mathematician");

        assertThrows(IllegalArgumentException.class, () -> instructor.reviseProfile(" ", "grace@example.com", "Computer scientist"));
        assertThrows(IllegalArgumentException.class, () -> instructor.reviseProfile("Grace Hopper", " ", "Computer scientist"));
    }
}
