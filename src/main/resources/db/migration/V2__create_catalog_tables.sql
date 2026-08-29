CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    status TEXT NOT NULL,
    CONSTRAINT categories_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT categories_status_valid CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE instructors (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    biography TEXT,
    CONSTRAINT instructors_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT instructors_email_not_blank CHECK (btrim(email) <> '')
);

CREATE TABLE courses (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    estimated_duration_hours INTEGER NOT NULL,
    level TEXT NOT NULL,
    price_amount NUMERIC NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    maximum_seats INTEGER NOT NULL,
    occupied_seats INTEGER NOT NULL,
    status TEXT NOT NULL,
    category_id UUID NOT NULL,
    instructor_id UUID NOT NULL,
    CONSTRAINT courses_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT courses_estimated_duration_hours_positive CHECK (estimated_duration_hours > 0),
    CONSTRAINT courses_level_valid CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT courses_price_amount_non_negative CHECK (price_amount >= 0),
    CONSTRAINT courses_currency_code_length CHECK (char_length(currency_code) = 3),
    CONSTRAINT courses_maximum_seats_positive CHECK (maximum_seats > 0),
    CONSTRAINT courses_occupied_seats_non_negative CHECK (occupied_seats >= 0),
    CONSTRAINT courses_occupied_seats_within_capacity CHECK (occupied_seats <= maximum_seats),
    CONSTRAINT courses_status_valid CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT courses_draft_has_no_occupied_seats CHECK (status <> 'DRAFT' OR occupied_seats = 0),
    CONSTRAINT courses_category_id_fkey FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT courses_instructor_id_fkey FOREIGN KEY (instructor_id) REFERENCES instructors (id) ON DELETE RESTRICT
);

CREATE INDEX courses_category_id_idx ON courses (category_id);
CREATE INDEX courses_instructor_id_idx ON courses (instructor_id);
CREATE INDEX courses_currency_code_price_amount_idx ON courses (currency_code, price_amount);
