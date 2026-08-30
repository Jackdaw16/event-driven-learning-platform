package io.github.jackdaw16.learningplatform.certificate.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCertificateRepository extends JpaRepository<CertificateJpaEntity, UUID> {

    Optional<CertificateJpaEntity> findByEnrollmentId(UUID enrollmentId);

    Optional<CertificateJpaEntity> findByVerificationCode(String verificationCode);
}
