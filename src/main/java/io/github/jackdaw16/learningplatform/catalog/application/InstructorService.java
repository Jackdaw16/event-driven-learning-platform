package io.github.jackdaw16.learningplatform.catalog.application;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ConflictException;
import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.InstructorRepository;
import io.github.jackdaw16.learningplatform.catalog.domain.Instructor;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstructorService {

    private final InstructorRepository instructorRepository;
    private final CourseRepository courseRepository;

    public InstructorService(InstructorRepository instructorRepository, CourseRepository courseRepository) {
        this.instructorRepository = instructorRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public Instructor create(String name, String email, String biography) {
        if (instructorRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("Instructor email already exists");
        }

        Instructor instructor = new Instructor(UUID.randomUUID(), name, email, biography);
        instructorRepository.save(instructor);
        return instructor;
    }

    @Transactional(readOnly = true)
    public Instructor getById(UUID id) {
        return findInstructor(id);
    }

    @Transactional(readOnly = true)
    public PageResult<Instructor> list(PageQuery pageQuery) {
        validateSort(pageQuery);
        return instructorRepository.list(pageQuery);
    }

    @Transactional
    public Instructor update(UUID id, String name, String email, String biography) {
        Instructor instructor = findInstructor(id);
        instructorRepository.findByEmail(email)
                .filter(existing -> !existing.id().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Instructor email already exists");
                });

        Instructor revised = instructor.reviseProfile(name, email, biography);
        instructorRepository.save(revised);
        return revised;
    }

    @Transactional
    public void delete(UUID id) {
        findInstructor(id);
        if (courseRepository.existsByInstructorId(id)) {
            throw new ConflictException("Instructor is referenced by courses");
        }
        instructorRepository.deleteById(id);
    }

    private Instructor findInstructor(UUID id) {
        return instructorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor", id));
    }

    private void validateSort(PageQuery pageQuery) {
        switch (pageQuery.sortField()) {
            case "name", "email" -> {
            }
            default -> throw new IllegalArgumentException("Unsupported instructor sort field: " + pageQuery.sortField());
        }
    }
}
