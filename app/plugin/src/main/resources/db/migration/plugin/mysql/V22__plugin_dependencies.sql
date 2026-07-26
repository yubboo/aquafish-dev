/* Aquafish MySQL / MariaDB V22：PF4J 插件依赖图。 */

CREATE TABLE IF NOT EXISTS `${tablePrefix}plugin_dependencies` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '依赖记录主键 ID',
    `plugin_id` BIGINT NOT NULL COMMENT '声明依赖的插件记录 ID',
    `dependency_key` VARCHAR(120) NOT NULL COMMENT '被依赖插件的稳定 plugin key',
    `version_requirement` VARCHAR(191) NOT NULL DEFAULT '*' COMMENT 'PF4J 版本范围表达式',
    `optional_flag` SMALLINT NOT NULL DEFAULT 0 COMMENT '是否为可选依赖',
    `resolved_plugin_id` BIGINT NULL COMMENT '本次解析命中的插件记录 ID',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING、RESOLVED、MISSING、INCOMPATIBLE',
    `last_error` VARCHAR(1000) NULL COMMENT '最近一次依赖解析失败摘要',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plugin_dependencies_plugin_dependency` (`plugin_id`, `dependency_key`),
    KEY `idx_plugin_dependencies_dependency_key` (`dependency_key`),
    KEY `idx_plugin_dependencies_resolution` (`status`, `optional_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PF4J 插件依赖声明与最近一次解析结果';
