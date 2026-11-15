CREATE TABLE tracks (
    id                UUID PRIMARY KEY,
    owner_id          UUID         NOT NULL,
    title             VARCHAR(300) NOT NULL,
    artist            VARCHAR(300),
    original_filename VARCHAR(500) NOT NULL,
    content_hash      CHAR(64)     NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    content_type      VARCHAR(50)  NOT NULL,
    object_key        VARCHAR(200) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    error             VARCHAR(500),
    keep_audio        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    analyzed_at       TIMESTAMP WITH TIME ZONE,
    CONSTRAINT tracks_owner_hash UNIQUE (owner_id, content_hash)
);
CREATE INDEX tracks_owner_created ON tracks (owner_id, created_at DESC);
CREATE INDEX tracks_owner_status ON tracks (owner_id, status);
CREATE INDEX tracks_awaiting ON tracks (created_at) WHERE status = 'AWAITING_UPLOAD';

CREATE TABLE analyses (
    track_id           UUID PRIMARY KEY REFERENCES tracks (id) ON DELETE CASCADE,
    job_id             UUID NOT NULL,
    model_version      VARCHAR(50),
    analyzer_version   VARCHAR(50),
    duration_sec       DOUBLE PRECISION,
    bpm                DOUBLE PRECISION,
    lufs               DOUBLE PRECISION,
    energy             DOUBLE PRECISION,
    energy_curve       JSONB,
    beat_times         JSONB,
    tonality           VARCHAR(20),
    key_tonic_pc       SMALLINT,
    key_mode           VARCHAR(5),
    camelot            VARCHAR(3),
    key_confidence     DOUBLE PRECISION,
    maqam              VARCHAR(20),
    maqam_confidence   DOUBLE PRECISION,
    maqam_uncertain    BOOLEAN,
    maqam_candidates   JSONB,
    tonic_cents        DOUBLE PRECISION,
    tonic_hz           DOUBLE PRECISION,
    tonic_note         VARCHAR(20),
    quarter_tone_ratio DOUBLE PRECISION,
    pitch_histogram    JSONB,
    timings            JSONB,
    edited_fields      JSONB NOT NULL DEFAULT '[]',
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL
);
-- "compatible next tracks" queries filter by tempo first
CREATE INDEX analyses_bpm ON analyses (bpm);
CREATE INDEX analyses_tonality ON analyses (tonality);

CREATE TABLE analysis_jobs (
    id          UUID PRIMARY KEY,
    track_id    UUID        NOT NULL,
    status      VARCHAR(10) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX analysis_jobs_track ON analysis_jobs (track_id);

CREATE TABLE corrections (
    id            UUID PRIMARY KEY,
    track_id      UUID        NOT NULL,
    user_id       UUID        NOT NULL,
    field         VARCHAR(20) NOT NULL,
    old_value     VARCHAR(300),
    new_value     VARCHAR(300),
    model_version VARCHAR(50),
    reviewed      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX corrections_review ON corrections (reviewed, created_at DESC);

CREATE TABLE outbox (
    id         BIGSERIAL PRIMARY KEY,
    topic      VARCHAR(100) NOT NULL,
    msg_key    VARCHAR(100),
    payload    TEXT         NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at    TIMESTAMP WITH TIME ZONE,
    attempts   INT          NOT NULL DEFAULT 0
);
CREATE INDEX outbox_unsent ON outbox (id) WHERE sent_at IS NULL;
