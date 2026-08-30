package io.github.jackdaw16.learningplatform.student.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.student.application.port.StudentRepository;
import io.github.jackdaw16.learningplatform.student.domain.Student;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StudentPersistenceAdapter implements StudentRepository {

    private final SpringDataStudentRepository repository;

    public StudentPersistenceAdapter(SpringDataStudentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(Student student) {
        repository.save(new StudentJpaEntity(
                student.id(),
                student.firstName(),
                student.lastName(),
                student.email(),
                student.registrationTimestamp()
        ));
    }

    @Override
    public Optional<Student> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    private Student toDomain(StudentJpaEntity entity) {
        return new Student(
                entity.id(),
                entity.firstName(),
                entity.lastName(),
                entity.email(),
                entity.registrationTimestamp()
        );
    }
}
