/*
 * Aquafish MySQL / MariaDB V4
 *
 * 数据库系统实例与首次安装状态机。
 *
 * 本迁移只创建表，不自动插入记录。
 * 旧版本安装状态将在后续兼容迁移服务中读取。
 */

CREATE TABLE IF NOT EXISTS ${tablePrefix}system_instances (
    singleton_id TINYINT UNSIGNED NOT NULL
        COMMENT '单例主键，只允许值 1',

    instance_id VARCHAR(36) NOT NULL
        COMMENT 'Aquafish 实例永久 UUID',

    installation_state VARCHAR(32) NOT NULL
        COMMENT 'UNINITIALIZED、INITIALIZING、INSTALLED 或 FAILED',

    state_version BIGINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '安装状态乐观并发版本',

    initialization_attempt_id VARCHAR(36) NULL
        COMMENT '当前初始化尝试 UUID',

    initialization_started_at DATETIME(3) NULL
        COMMENT '当前初始化尝试开始时间',

    installed_at DATETIME(3) NULL
        COMMENT '首次安装完成时间',

    installed_version VARCHAR(64) NULL
        COMMENT '首次安装完成时的 Aquafish 版本',

    last_error_code VARCHAR(100) NULL
        COMMENT '最近一次初始化失败的安全错误码',

    last_error_message VARCHAR(500) NULL
        COMMENT '最近一次初始化失败的脱敏错误摘要',

    created_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '记录创建时间',

    updated_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '记录更新时间',

    PRIMARY KEY (singleton_id),

    UNIQUE KEY uk_system_instances_instance_id
        (instance_id),

    CHECK (singleton_id = 1),

    CHECK (
        installation_state IN (
            'UNINITIALIZED',
            'INITIALIZING',
            'INSTALLED',
            'FAILED'
        )
    )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Aquafish 数据库系统实例与首次安装状态';
