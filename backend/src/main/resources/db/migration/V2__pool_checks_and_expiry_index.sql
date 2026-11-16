-- Phase 3: database-level guards for pool rules, and an index for the expiry job.

-- The API validates these too; the CHECKs keep any other writer honest.
ALTER TABLE pools
    ADD CONSTRAINT pools_target_amount_positive_ck CHECK (target_amount_paise > 0);

ALTER TABLE pool_items
    ADD CONSTRAINT pool_items_quantity_positive_ck CHECK (quantity >= 1),
    ADD CONSTRAINT pool_items_est_price_positive_ck CHECK (est_price_paise > 0);

-- The expiry job runs every minute: "status = 'OPEN' AND closes_at <= now". A partial index stays
-- small because only OPEN pools are in it.
CREATE INDEX pools_open_closes_at_idx ON pools (closes_at) WHERE status = 'OPEN';
