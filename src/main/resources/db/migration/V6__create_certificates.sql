CREATE TABLE certificates (
    id UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL UNIQUE,
    verification_code VARCHAR(36) NOT NULL UNIQUE,
    issued_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT certificates_enrollment_id_fkey FOREIGN KEY (enrollment_id) REFERENCES enrollments (id) ON DELETE RESTRICT,
    CONSTRAINT certificates_verification_code_not_blank CHECK (btrim(verification_code) <> '')
);
