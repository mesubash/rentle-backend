-- users (phone/email uniques already exist as constraints)
CREATE INDEX idx_users_status ON users(status);

-- listings
CREATE INDEX idx_listings_owner        ON listings(owner_id);
CREATE INDEX idx_listings_category     ON listings(category_id);
CREATE INDEX idx_listings_type_status  ON listings(type, status);
CREATE INDEX idx_listings_district     ON listings(district);
CREATE INDEX idx_listings_search       ON listings USING gin(search_vector);
CREATE INDEX idx_listings_rating       ON listings(average_rating DESC NULLS LAST)
                                        WHERE status = 'ACTIVE';

-- bookings
CREATE INDEX idx_bookings_listing      ON bookings(listing_id);
CREATE INDEX idx_bookings_renter       ON bookings(renter_id);
CREATE INDEX idx_bookings_status       ON bookings(status);
CREATE INDEX idx_bookings_dates        ON bookings(listing_id, start_date, end_date);

-- messages
CREATE INDEX idx_messages_booking      ON messages(booking_id, created_at DESC);

-- reviews
CREATE INDEX idx_reviews_listing       ON reviews(listing_id);
CREATE INDEX idx_reviews_subject       ON reviews(subject_id);
CREATE INDEX idx_reviews_booking       ON reviews(booking_id);

-- listing_images
CREATE INDEX idx_images_listing        ON listing_images(listing_id, sort_order);

-- unavailable_ranges
CREATE INDEX idx_unavailable_listing   ON unavailable_ranges(listing_id);
