-- Stores the AI-generated review for each PR.
-- V3 because V1 and V2 already ran in ingestion-service against the same DB.

CREATE TABLE reviews (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repo_full_name  VARCHAR(255)     NOT NULL,
    pr_number       BIGINT           NOT NULL,
    head_sha        VARCHAR(64)      NOT NULL,
    delivery_id     VARCHAR(255)     NOT NULL UNIQUE,
    status          VARCHAR(32)      NOT NULL DEFAULT 'PENDING',
    review_body     TEXT,
    model_used      VARCHAR(64),
    chunks_used     INTEGER,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_reviews_repo_pr ON reviews (repo_full_name, pr_number);
CREATE INDEX idx_reviews_status ON reviews (status);