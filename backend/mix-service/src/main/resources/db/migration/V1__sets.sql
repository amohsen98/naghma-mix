CREATE TABLE dj_sets (
    id               UUID PRIMARY KEY,
    owner_id         UUID         NOT NULL,
    name             VARCHAR(100) NOT NULL,
    curve            VARCHAR(16)  NOT NULL,
    track_ids        JSONB        NOT NULL,
    locked_positions JSONB        NOT NULL,
    w_harmony        DOUBLE PRECISION NOT NULL,
    w_tonic          DOUBLE PRECISION NOT NULL,
    w_tempo          DOUBLE PRECISION NOT NULL,
    w_energy         DOUBLE PRECISION NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX dj_sets_owner_updated ON dj_sets (owner_id, updated_at DESC);
