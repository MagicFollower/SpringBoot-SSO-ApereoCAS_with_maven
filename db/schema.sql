CREATE DATABASE IF NOT EXISTS cas_sso
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE cas_sso;

CREATE TABLE IF NOT EXISTS t_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(128) NULL,
    dept_id       VARCHAR(64)  NULL,
    enabled       TINYINT      NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS t_channel (
    channel_code VARCHAR(32)  NOT NULL,
    channel_name VARCHAR(128) NOT NULL,
    status       TINYINT      NOT NULL DEFAULT 1,
    theme        VARCHAR(64)  NULL,
    mfa_required TINYINT      NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (channel_code)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS t_user_channel (
    user_id      VARCHAR(64) NOT NULL,
    channel_code VARCHAR(32) NOT NULL,
    source       VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    valid_from   DATETIME    NULL,
    valid_to     DATETIME    NULL,
    status       TINYINT     NOT NULL DEFAULT 1,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, channel_code),
    KEY idx_user_channel_user_status (user_id, status),
    KEY idx_user_channel_channel_status (channel_code, status),
    CONSTRAINT fk_user_channel_user
        FOREIGN KEY (user_id) REFERENCES t_user (username)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_user_channel_channel
        FOREIGN KEY (channel_code) REFERENCES t_channel (channel_code)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS t_login_audit (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      VARCHAR(64)  NULL,
    channel_code VARCHAR(32)  NULL,
    service_url  VARCHAR(512) NULL,
    result       VARCHAR(16)  NOT NULL,
    reason       VARCHAR(256) NULL,
    ip           VARCHAR(64)  NULL,
    user_agent   VARCHAR(512) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_login_audit_user_time (user_id, created_at),
    KEY idx_login_audit_channel_time (channel_code, created_at)
) ENGINE = InnoDB;
