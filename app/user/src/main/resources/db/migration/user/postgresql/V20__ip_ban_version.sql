/*
 * IP 封禁规则补充地址版本。
 *
 * 4 = IPv4，6 = IPv6，0 = 历史自定义规则或尚未识别。
 */
ALTER TABLE ${tablePrefix}ip_bans
    ADD COLUMN IF NOT EXISTS ip_version SMALLINT NOT NULL DEFAULT 0;

UPDATE ${tablePrefix}ip_bans
SET ip_version = CASE
    WHEN POSITION(':' IN ip_value) > 0 THEN 6
    WHEN ip_value ~ '^[0-9.]+(/[0-9]+)?$' THEN 4
    ELSE 0
END
WHERE ip_version = 0;

CREATE INDEX IF NOT EXISTS idx_ip_bans_version_enabled
    ON ${tablePrefix}ip_bans (ip_version, enabled);
