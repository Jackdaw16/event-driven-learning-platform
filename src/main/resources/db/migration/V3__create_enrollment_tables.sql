CREATE TABLE students (
    id UUID PRIMARY KEY,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    registration_timestamp TIMESTAMPTZ NOT NULL,
    CONSTRAINT students_first_name_not_blank CHECK (btrim(first_name) <> ''),
    CONSTRAINT students_last_name_not_blank CHECK (btrim(last_name) <> ''),
    CONSTRAINT students_email_not_blank CHECK (btrim(email) <> '')
);

CREATE TABLE enrollments (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL,
    course_id UUID NOT NULL,
    status TEXT NOT NULL,
    progress INTEGER NOT NULL,
    enrolled_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT enrollments_student_id_fkey FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE RESTRICT,
    CONSTRAINT enrollments_course_id_fkey FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE RESTRICT,
    CONSTRAINT enrollments_status_valid CHECK (status IN ('PENDING_PAYMENT', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT enrollments_progress_within_range CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT enrollments_lifecycle_state_consistent CHECK (
        (status = 'PENDING_PAYMENT' AND progress = 0 AND completed_at IS NULL)
        OR (status = 'ACTIVE' AND progress BETWEEN 0 AND 99 AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND progress = 100 AND completed_at IS NOT NULL)
        OR (status = 'CANCELLED' AND progress BETWEEN 0 AND 99 AND completed_at IS NULL)
    )
);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL UNIQUE,
    amount NUMERIC NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    status TEXT NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT payments_enrollment_id_fkey FOREIGN KEY (enrollment_id) REFERENCES enrollments (id) ON DELETE RESTRICT,
    CONSTRAINT payments_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT payments_currency_code_length CHECK (char_length(currency_code) = 3),
    CONSTRAINT payments_status_valid CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED')),
    CONSTRAINT payments_idempotency_key_not_blank CHECK (btrim(idempotency_key) <> '')
);

CREATE UNIQUE INDEX enrollments_live_student_course_unique_idx
    ON enrollments (student_id, course_id)
    WHERE status IN ('PENDING_PAYMENT', 'ACTIVE', 'COMPLETED');

CREATE INDEX enrollments_student_id_idx ON enrollments (student_id);
CREATE INDEX enrollments_course_id_idx ON enrollments (course_id);
