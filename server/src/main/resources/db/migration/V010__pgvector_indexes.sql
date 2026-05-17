CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE recipes ADD COLUMN embedding_recipe vector(384);
CREATE INDEX idx_recipes_embedding ON recipes USING ivfflat (embedding_recipe vector_cosine_ops);

ALTER TABLE food_composition ADD COLUMN embedding_food vector(384);
CREATE INDEX idx_food_embedding ON food_composition USING ivfflat (embedding_food vector_cosine_ops);
