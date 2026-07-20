-- Gradual category rollout (docs/12): launch narrow, add categories seasonally.
-- Phase-1 launch decision (docs/12 §10): Cameras & Tech + Event & Photography go live;
-- Moving & Transport and Traditional Clothing start hidden (is_active = false) and are
-- relaunched later from the admin console. is_active is the launch/pause switch until the
-- full DRAFT/ACTIVE/PAUSED status enum lands with the category platform.
UPDATE categories SET is_active = false
WHERE slug IN ('moving-transport', 'traditional-clothing');
