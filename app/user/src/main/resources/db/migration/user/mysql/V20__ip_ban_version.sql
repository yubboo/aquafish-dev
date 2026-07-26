/*
 * IP 封禁规则补充地址版本。
 *
 * 4 = IPv4，6 = IPv6，0 = 历史自定义规则或尚未识别。
 * 字段只用于后台筛选和诊断，真正匹配仍由统一 IP 规则解析器执行。
 */
ALTER TABLE `${tablePrefix}ip_bans`
    ADD COLUMN `ip_version` SMALLINT NOT NULL DEFAULT 0
        COMMENT 'IP 地址版本：0 未识别、4 IPv4、6 IPv6'
        AFTER `ip_value`;

UPDATE `${tablePrefix}ip_bans`
SET `ip_version` = CASE
    WHEN `ip_value` LIKE '%:%' THEN 6
    WHEN `ip_value` REGEXP '^[0-9.]+(/[0-9]+)?$' THEN 4
    ELSE 0
END
WHERE `ip_version` = 0;

CREATE INDEX `idx_ip_bans_version_enabled`
    ON `${tablePrefix}ip_bans` (`ip_version`, `enabled`);
