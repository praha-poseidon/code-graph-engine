CREATE TABLE IF NOT EXISTS repository_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    git_repo_url VARCHAR(2048) NOT NULL,
    git_branch VARCHAR(255) NOT NULL,
    languages VARCHAR(512) NOT NULL,
    auth_type VARCHAR(32) NOT NULL,
    access_token TEXT,
    ssh_private_key TEXT,
    ssh_passphrase TEXT,
    endpoint_rule_sources TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'IDLE',
    last_analyzed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repository_config_url UNIQUE (git_repo_url)
);

CREATE TABLE IF NOT EXISTS analysis_task (
    id VARCHAR(36) PRIMARY KEY,
    repository_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    progress_current INT NOT NULL DEFAULT 0,
    progress_total INT NOT NULL DEFAULT 0,
    message VARCHAR(1024),
    error_details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    INDEX idx_analysis_task_status_created (status, created_at),
    INDEX idx_analysis_task_repository_created (repository_id, created_at),
    CONSTRAINT fk_analysis_task_repository
        FOREIGN KEY (repository_id) REFERENCES repository_config(id) ON DELETE CASCADE
);
