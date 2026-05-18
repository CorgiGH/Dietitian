-- V014__pgvector_dim_1024_hnsw.sql
-- Per Plan-3 A1 + A6: drop locked-spec V010 recipes.embedding_recipe(384) column.
-- Replace with unified corpus_embeddings(corpus, item_id, embedding VECTOR(1024)) + HNSW.
-- embedding_provider_version recorded for drift tracking (Voyage-4-Lite vs BGE-M3 etc).
-- Also drops V010's food_composition.embedding_food(384) for the same reason.

DROP INDEX IF EXISTS idx_recipes_embedding;
ALTER TABLE recipes DROP COLUMN IF EXISTS embedding_recipe;

DROP INDEX IF EXISTS idx_food_embedding;
ALTER TABLE food_composition DROP COLUMN IF EXISTS embedding_food;

CREATE TABLE corpus_embeddings (
    corpus                       TEXT NOT NULL CHECK (corpus IN ('papers', 'recipes', 'foods', 'supplements', 'skus')),
    item_id                      TEXT NOT NULL,
    embedding                    VECTOR(1024) NOT NULL,
    embedding_provider_version   TEXT NOT NULL,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (corpus, item_id)
);

CREATE INDEX idx_corpus_embeddings_hnsw ON corpus_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX idx_corpus_embeddings_corpus ON corpus_embeddings (corpus);
