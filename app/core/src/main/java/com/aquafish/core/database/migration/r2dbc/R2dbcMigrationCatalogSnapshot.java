package com.aquafish.core.database.migration.r2dbc;

import java.util.List;
import java.util.Optional;

/**
 * Aquafish 当前 classpath 中全部正式迁移版本快照。
 *
 * @param entries 按版本升序排列的迁移条目
 * @param latestVersion 当前代码包含的最高迁移版本
 */
public record R2dbcMigrationCatalogSnapshot(
    List<R2dbcMigrationCatalogEntry> entries,
    long latestVersion
) {

    public R2dbcMigrationCatalogSnapshot {
        entries =
            entries == null
                ? List.of()
                : List.copyOf(entries);

        long previous = 0;

        for (
            R2dbcMigrationCatalogEntry entry
                : entries
        ) {
            if (entry == null) {
                throw new IllegalArgumentException(
                    "迁移目录不能包含空条目。"
                );
            }

            if (
                entry.version()
                    <= previous
            ) {
                throw new IllegalArgumentException(
                    "迁移目录必须按版本严格升序排列。"
                );
            }

            previous =
                entry.version();
        }

        long expectedLatest =
            entries.isEmpty()
                ? 0
                : entries
                    .getLast()
                    .version();

        if (
            latestVersion
                != expectedLatest
        ) {
            throw new IllegalArgumentException(
                "迁移目录最高版本不一致。"
            );
        }
    }

    /**
     * 当前数据库版本之后还有多少个迁移。
     */
    public int pendingAfter(
        long currentVersion
    ) {
        return Math.toIntExact(
            entries
                .stream()
                .filter(
                    entry ->
                        entry.version()
                            > currentVersion
                )
                .count()
        );
    }

    /**
     * 返回当前数据库版本之后的迁移条目。
     */
    public List<R2dbcMigrationCatalogEntry>
        entriesAfter(
            long currentVersion
        ) {

        return entries
            .stream()
            .filter(
                entry ->
                    entry.version()
                        > currentVersion
            )
            .toList();
    }

    /**
     * 根据版本查找迁移条目。
     */
    public Optional<R2dbcMigrationCatalogEntry>
        find(
            long version
        ) {

        return entries
            .stream()
            .filter(
                entry ->
                    entry.version()
                        == version
            )
            .findFirst();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
