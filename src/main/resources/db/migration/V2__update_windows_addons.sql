-- V2: Expand generic image-windows addon into three versioned entries
-- (2016, 2019, 2022) preserving the same priceMonthly for each variant.
-- Plans with no image-windows entry are unaffected.

UPDATE plans
SET available_addons = (
    SELECT jsonb_agg(new_elem)
    FROM (
        SELECT elem AS new_elem
        FROM jsonb_array_elements(available_addons) elem
        WHERE elem->>'id' != 'image-windows'

        UNION ALL

        SELECT jsonb_set(elem, '{id}', '"image-windows-2016"'::jsonb)
        FROM jsonb_array_elements(available_addons) elem
        WHERE elem->>'id' = 'image-windows'

        UNION ALL

        SELECT jsonb_set(elem, '{id}', '"image-windows-2019"'::jsonb)
        FROM jsonb_array_elements(available_addons) elem
        WHERE elem->>'id' = 'image-windows'

        UNION ALL

        SELECT jsonb_set(elem, '{id}', '"image-windows-2022"'::jsonb)
        FROM jsonb_array_elements(available_addons) elem
        WHERE elem->>'id' = 'image-windows'
    ) sub
)
WHERE available_addons @> '[{"id": "image-windows"}]'::jsonb;
