CREATE TABLE availability_periods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accommodation_id UUID NOT NULL REFERENCES accommodations(id) ON DELETE CASCADE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    price_per_day NUMERIC(12, 2) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT chk_availability_dates CHECK (end_date > start_date),
    CONSTRAINT chk_price_positive CHECK (price_per_day > 0)
);

CREATE INDEX idx_availability_periods_accommodation_id ON availability_periods(accommodation_id);
CREATE INDEX idx_availability_periods_dates ON availability_periods(start_date, end_date);
CREATE INDEX idx_availability_periods_accommodation_dates
    ON availability_periods(accommodation_id, start_date, end_date)
    WHERE is_deleted = false;
