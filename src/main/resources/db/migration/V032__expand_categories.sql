-- Broaden the live catalogue so the marketplace looks populated. Activates the two categories
-- that shipped paused, and adds a spread of everyday rental and service categories.

UPDATE categories SET is_active = true WHERE slug IN ('moving-transport', 'traditional-clothing');

INSERT INTO categories (id, name, slug, listing_type, icon_name, sort_order, is_active) VALUES
    (gen_random_uuid(), 'Bikes & Scooters',   'bikes-scooters',   'PRODUCT', 'bike',       5, true),
    (gen_random_uuid(), 'Tools & Equipment',  'tools-equipment',  'PRODUCT', 'wrench',     6, true),
    (gen_random_uuid(), 'Outdoor & Camping',  'outdoor-camping',  'PRODUCT', 'tent',       7, true),
    (gen_random_uuid(), 'Party & Events',     'party-events',     'PRODUCT', 'party',      8, true),
    (gen_random_uuid(), 'Home Services',      'home-services',    'SERVICE', 'home',       9, true),
    (gen_random_uuid(), 'Tutoring & Lessons', 'tutoring-lessons', 'SERVICE', 'book',      10, true);
