-- PostgreSQL named enums
CREATE TYPE pricing_mode AS ENUM ('PER_GUEST', 'PER_UNIT');
CREATE TYPE approval_mode AS ENUM ('AUTOMATIC', 'MANUAL');
CREATE TYPE amenity_type AS ENUM ('WIFI', 'KITCHEN', 'AC', 'PARKING', 'FREE_PARKING', 'POOL', 'TV', 'WASHING_MACHINE', 'HEATING', 'BALCONY');

-- Accommodations table
CREATE TABLE accommodations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    min_guests INTEGER NOT NULL,
    max_guests INTEGER NOT NULL,
    pricing_mode pricing_mode NOT NULL,
    approval_mode approval_mode NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Amenities table
CREATE TABLE amenities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accommodation_id UUID NOT NULL REFERENCES accommodations(id) ON DELETE CASCADE,
    type amenity_type NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_accommodations_host_id ON accommodations(host_id);
CREATE INDEX idx_amenities_accommodation_id ON amenities(accommodation_id);

-- Partial unique index: one amenity type per accommodation (among non-deleted)
CREATE UNIQUE INDEX idx_amenities_unique_type
    ON amenities(accommodation_id, type)
    WHERE is_deleted = false;
