-- Migrate amenities from separate table to array column on accommodations

-- Add amenities array column
ALTER TABLE accommodations ADD COLUMN amenities amenity_type[] NOT NULL DEFAULT '{}';

-- Migrate existing data
UPDATE accommodations a
SET amenities = (
    SELECT COALESCE(array_agg(am.type), '{}')
    FROM amenities am
    WHERE am.accommodation_id = a.id AND am.is_deleted = false
);

-- Drop amenities table and its indexes
DROP TABLE amenities;
