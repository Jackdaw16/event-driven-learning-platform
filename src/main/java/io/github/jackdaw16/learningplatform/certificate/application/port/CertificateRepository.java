package io.github.jackdaw16.learningplatform.certificate.application.port;

import io.github.jackdaw16.learningplatform.certificate.domain.Certificate;
import java.util.Optional;
import java.util.UUID;

public interface CertificateRepository {

    void save(Certificate certificate);

    Optional<Certificate> findByEnrollmentId(UUID enrollmentId);

    Optional<Certificate> findByVerificationCode(String verificationCode);
}
