package io.github.jackdaw16.learningplatform.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ConflictException;
import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.InstructorRepository;
import io.github.jackdaw16.learningplatform.catalog.domain.Instructor;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InstructorServiceTest {

    private final InstructorRepository instructorRepository = mock(InstructorRepository.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final InstructorService service = new InstructorService(instructorRepository, courseRepository);

    @Test
    void createsInstructorAndSavesIt() {
        when(instructorRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());

        Instructor created = service.create("Ada Lovelace", "ada@example.com", "Mathematician");

        assertEquals("Ada Lovelace", created.name());
        assertEquals("ada@example.com", created.email());
        assertEquals("Mathematician", created.biography());
        verify(instructorRepository).save(created);
    }

    @Test
    void rejectsDuplicateInstructorEmailDuringCreation() {
        when(instructorRepository.findByEmail("ada@example.com"))
                .thenReturn(Optional.of(new Instructor(UUID.randomUUID(), "Ada Lovelace", "ada@example.com", null)));

        assertThrows(ConflictException.class, () -> service.create("Another Ada", "ada@example.com", null));

        verify(instructorRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updatesInstructorWithSameIdentityAndSavesRevisedProfile() {
        UUID id = UUID.randomUUID();
        Instructor instructor = new Instructor(id, "Ada Lovelace", "ada@example.com", "Mathematician");
        when(instructorRepository.findById(id)).thenReturn(Optional.of(instructor));
        when(instructorRepository.findByEmail("grace@example.com")).thenReturn(Optional.empty());

        Instructor updated = service.update(id, "Grace Hopper", "grace@example.com", "Computer scientist");

        assertEquals(id, updated.id());
        assertEquals("Grace Hopper", updated.name());
        assertEquals("grace@example.com", updated.email());
        assertEquals("Computer scientist", updated.biography());
        verify(instructorRepository).save(updated);
    }

    @Test
    void permitsUpdatingInstructorWithItsOwnEmail() {
        UUID id = UUID.randomUUID();
        Instructor instructor = new Instructor(id, "Ada Lovelace", "ada@example.com", "Mathematician");
        when(instructorRepository.findById(id)).thenReturn(Optional.of(instructor));
        when(instructorRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(instructor));

        Instructor updated = service.update(id, "Ada Byron", "ada@example.com", "Pioneer");

        assertEquals(id, updated.id());
        assertEquals("Ada Byron", updated.name());
        verify(instructorRepository).save(updated);
    }

    @Test
    void deletesUnreferencedInstructorPhysically() {
        Instructor instructor = new Instructor(UUID.randomUUID(), "Ada Lovelace", "ada@example.com", "Mathematician");
        when(instructorRepository.findById(instructor.id())).thenReturn(Optional.of(instructor));
        when(courseRepository.existsByInstructorId(instructor.id())).thenReturn(false);

        service.delete(instructor.id());

        verify(instructorRepository).deleteById(instructor.id());
    }

    @Test
    void rejectsDeletingInstructorReferencedByCourses() {
        Instructor instructor = new Instructor(UUID.randomUUID(), "Ada Lovelace", "ada@example.com", "Mathematician");
        when(instructorRepository.findById(instructor.id())).thenReturn(Optional.of(instructor));
        when(courseRepository.existsByInstructorId(instructor.id())).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.delete(instructor.id()));

        verify(instructorRepository, never()).deleteById(instructor.id());
    }

    @Test
    void reportsMissingInstructor() {
        UUID id = UUID.randomUUID();
        when(instructorRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getById(id));
    }
}
