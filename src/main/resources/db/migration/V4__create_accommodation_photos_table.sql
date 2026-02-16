CREATE TABLE accommodation_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accommodation_id UUID NOT NULL REFERENCES accommodations(id) ON DELETE CASCADE,
    storage_filename VARCHAR(255) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_accommodation_photos_accommodation_id ON accommodation_photos(accommodation_id);
CREATE INDEX idx_accommodation_photos_display_order ON accommodation_photos(accommodation_id, display_order);

ALTER TABLE accommodation_photos ADD CONSTRAINT chk_content_type
    CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp'));
