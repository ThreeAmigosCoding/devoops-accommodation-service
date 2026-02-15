-- Change amenities column from amenity_type[] to text[] for Hibernate compatibility
ALTER TABLE accommodations ALTER COLUMN amenities TYPE text[] USING amenities::text[];
