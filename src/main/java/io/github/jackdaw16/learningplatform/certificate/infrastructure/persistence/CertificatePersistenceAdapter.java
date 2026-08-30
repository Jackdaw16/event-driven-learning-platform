package io.github.jackdaw16.learningplatform.certificate.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.certificate.application.port.CertificateRepository;
import io.github.jackdaw16.learningplatform.certificate.domain.Certificate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CertificatePersistenceAdapter implements CertificateRepository {

    private final SpringDataCertificateRepository repository;

    public CertificatePersistenceAdapter(SpringDataCertificateRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(Certificate certificate) {
        repository.save(new CertificateJpaEntity(
                certificate.id(),
                certificate.enrollmentId(),
                certificate.verificationCode(),
                certificate.issuedAt()
        ));
    }

    @Override
    public Optional<Certificate> findByEnrollmentId(UUID enrollmentId) {
        return repository.findByEnrollmentId(enrollmentId).map(this::toDomain);
    }

    @Override
    public Optional<Certificate> findByVerificationCode(String verificationCode) {
        return repository.findByVerificationCode(verificationCode).map(this::toDomain);
    }

    private Certificate toDomain(CertificateJpaEntity entity) {
        return new Certificate(entity.id(), entity.enrollmentId(), entity.verificationCode(), entity.issuedAt());
    }
}
