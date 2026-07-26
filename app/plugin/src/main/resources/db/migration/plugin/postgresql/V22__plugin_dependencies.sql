/* Aquafish PostgreSQL V22：PF4J 插件依赖图。 */

CREATE TABLE IF NOT EXISTS "${tablePrefix}plugin_dependencies" (
    "id" BIGSERIAL PRIMARY KEY,
    "plugin_id" BIGINT NOT NULL,
    "dependency_key" VARCHAR(120) NOT NULL,
    "version_requirement" VARCHAR(191) NOT NULL DEFAULT '*',
    "optional_flag" SMALLINT NOT NULL DEFAULT 0,
    "resolved_plugin_id" BIGINT NULL,
    "status" VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    "last_error" VARCHAR(1000) NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "uk_plugin_dependencies_plugin_dependency"
        UNIQUE ("plugin_id", "dependency_key")
);

CREATE INDEX IF NOT EXISTS "idx_plugin_dependencies_dependency_key"
    ON "${tablePrefix}plugin_dependencies" ("dependency_key");

CREATE INDEX IF NOT EXISTS "idx_plugin_dependencies_resolution"
    ON "${tablePrefix}plugin_dependencies" ("status", "optional_flag");
