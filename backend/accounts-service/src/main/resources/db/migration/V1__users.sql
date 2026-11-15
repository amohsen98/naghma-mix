CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(80)  NOT NULL,
    tier          VARCHAR(16)  NOT NULL DEFAULT 'FREE',
    locale        VARCHAR(5)   NOT NULL DEFAULT 'en',
    admin         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
