INSERT INTO categories (id, name, slug, listing_type, icon_name, sort_order) VALUES
(gen_random_uuid(), 'Cameras & Tech',       'cameras-tech',         'PRODUCT', 'camera',   1),
(gen_random_uuid(), 'Traditional Clothing', 'traditional-clothing', 'PRODUCT', 'shirt',    2),
(gen_random_uuid(), 'Moving & Transport',   'moving-transport',     'SERVICE', 'truck',    3),
(gen_random_uuid(), 'Event & Photography',  'event-photography',    'SERVICE', 'aperture', 4);
