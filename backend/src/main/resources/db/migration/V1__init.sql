-- Phase 1: full schema from docs/SPEC.md "Data model".

CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(100)  NOT NULL,
    email         VARCHAR(255)  NOT NULL UNIQUE,
    password_hash VARCHAR(100)  NOT NULL,
    upi_id        VARCHAR(100),
    created_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT users_email_lower_ck CHECK (email = lower(email))
);

CREATE TABLE communities (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    invite_code VARCHAR(8)   NOT NULL UNIQUE,
    created_by  BIGINT       NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT communities_invite_code_len_ck CHECK (char_length(invite_code) = 8)
);

CREATE TABLE memberships (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    community_id BIGINT      NOT NULL REFERENCES communities (id),
    role         VARCHAR(10) NOT NULL,
    joined_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT memberships_user_community_uq UNIQUE (user_id, community_id),
    CONSTRAINT memberships_role_ck CHECK (role IN ('ADMIN', 'MEMBER'))
);

CREATE TABLE pools (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    community_id        BIGINT       NOT NULL REFERENCES communities (id),
    host_id             BIGINT       NOT NULL REFERENCES users (id),
    platform            VARCHAR(20)  NOT NULL,
    title               VARCHAR(200) NOT NULL,
    target_amount_paise BIGINT       NOT NULL,
    closes_at           TIMESTAMPTZ  NOT NULL,
    pickup_spot         VARCHAR(200) NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    fees_paise          BIGINT,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pools_platform_ck CHECK (platform IN ('BLINKIT', 'INSTAMART', 'ZEPTO', 'BIGBASKET', 'OTHER')),
    CONSTRAINT pools_status_ck CHECK (status IN ('OPEN', 'LOCKED', 'EXPIRED', 'CANCELLED', 'ORDERED', 'DELIVERED', 'SETTLED'))
);

CREATE INDEX pools_community_id_idx ON pools (community_id);

CREATE TABLE pool_members (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pool_id     BIGINT      NOT NULL REFERENCES pools (id),
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    share_paise BIGINT,
    paid        BOOLEAN     NOT NULL DEFAULT FALSE,
    paid_at     TIMESTAMPTZ,
    CONSTRAINT pool_members_pool_user_uq UNIQUE (pool_id, user_id)
);

CREATE INDEX pool_members_pool_id_idx ON pool_members (pool_id);

CREATE TABLE pool_items (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pool_member_id      BIGINT       NOT NULL REFERENCES pool_members (id),
    name                VARCHAR(200) NOT NULL,
    quantity            INTEGER      NOT NULL,
    est_price_paise     BIGINT       NOT NULL,
    final_price_paise   BIGINT
);

CREATE INDEX pool_items_pool_member_id_idx ON pool_items (pool_member_id);

CREATE TABLE messages (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pool_id     BIGINT      NOT NULL REFERENCES pools (id),
    sender_id   BIGINT      NOT NULL REFERENCES users (id),
    body        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX messages_pool_id_idx ON messages (pool_id);
