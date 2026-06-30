-- voyage-code-2 outputs 1536-dimensional embeddings; original schema assumed 1024.
-- Safe to ALTER directly since no real embeddings exist yet (all rows are NULL).

ALTER TABLE code_chunks ALTER COLUMN embedding TYPE vector(1536);
