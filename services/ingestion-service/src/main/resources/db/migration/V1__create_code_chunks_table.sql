-- Stores chunks of source code along with their vector embeddings,
-- so review-service can later do semantic similarity search (RAG retrieval).

CREATE TABLE code_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repo_full_name  VARCHAR(255)     NOT NULL,
    file_path       VARCHAR(1024)    NOT NULL,
    chunk_index     INTEGER          NOT NULL,
    content         TEXT             NOT NULL,
    embedding       vector(1024),
    head_sha        VARCHAR(64)      NOT NULL,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- Speeds up "find all chunks for this repo" and "find chunks for this exact commit"
CREATE INDEX idx_code_chunks_repo ON code_chunks (repo_full_name);
CREATE INDEX idx_code_chunks_repo_sha ON code_chunks (repo_full_name, head_sha);

-- HNSW index for fast approximate nearest-neighbor search on embeddings.
-- This is what makes "find similar code" queries fast even with millions of rows.
CREATE INDEX idx_code_chunks_embedding ON code_chunks
    USING hnsw (embedding vector_cosine_ops);