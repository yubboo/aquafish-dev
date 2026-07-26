/*
 * Aquafish PostgreSQL V4
 *
 * 数据库系统实例与首次安装状态机。
 *
 * 本迁移只创建表，不自动插入记录。
 * 旧版本安装状态将在后续兼容迁移服务中读取。
 */

CREATE TABLE IF NOT EXISTS ${tablePrefix}system_instances (
    singleton_id SMALLINT NOT NULL,

    instance_id VARCHAR(36) NOT NULL,

    installation_state VARCHAR(32) NOT NULL,

    state_version BIGINT NOT NULL DEFAULT 0,

    initialization_attempt_id VARCHAR(36),

    initialization_started_at TIMESTAMP(3),

    installed_at TIMESTAMP(3),

    installed_version VARCHAR(64),

    last_error_code VARCHAR(100),

    last_error_message VARCHAR(500),

    created_at TIMESTAMP(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (singleton_id),

    UNIQUE (instance_id),

    CHECK (singleton_id = 1),

    CHECK (
        installation_state IN (
            'UNINITIALIZED',
            'INITIALIZING',
            'INSTALLED',
            'FAILED'
        )
    )
);

COMMENT ON TABLE
    ${tablePrefix}system_instances
    IS 'Aquafish 数据库系统实例与首次安装状态';

COMMENT ON COLUMN
    ${tablePrefix}system_instances.singleton_id
    IS '单例主键，只允许值 1';

COMMENT ON COLUMN
    ${tablePrefix}system_instances.instance_id
    IS 'Aquafish 实例永久 UUID';

COMMENT ON COLUMN
    ${tablePrefix}system_instances.state_version
    IS '安装状态乐观并发版本';
