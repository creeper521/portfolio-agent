package com.portfolio.agent.portfolio.service;

import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 公开发布的激活服务：把一个已通过校验的发布切换为当前生效发布。
 *
 * <p>面向公开 PostgreSQL 投影库，在单个事务内原子完成两步：仅当发布状态为 VERIFIED 时
 * 将其更新为 PUBLISHED 并写入 published_at，同时更新 active_release 单例表指向该发布。
 * 单条 SQL 语句 + 事务保证并发激活不会出现"已发布但未生效"或"生效指向未发布版本"的中间态。
 */
public final class PublicReleaseActivationService {

    /**
     * 原子激活 SQL：条件更新 VERIFIED 发布为 PUBLISHED，并把 active_release 单例行
     * 指向该发布。CTE 保证只有更新成功的发布才会进入 active_release，冲突时覆盖旧行。
     */
    private static final String ACTIVATE_RELEASE_SQL = """
            WITH verified_release AS (
                UPDATE content_release
                SET status = 'PUBLISHED', published_at = now()
                WHERE release_id = CAST(? AS uuid)
                  AND status = 'VERIFIED'
                RETURNING release_id
            )
            INSERT INTO active_release (singleton, release_id, activated_at)
            SELECT true, release_id, now()
            FROM verified_release
            ON CONFLICT (singleton) DO UPDATE
            SET release_id = EXCLUDED.release_id,
                activated_at = EXCLUDED.activated_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionOperations transactions;

    public PublicReleaseActivationService(JdbcTemplate jdbcTemplate, TransactionOperations transactions) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    /**
     * 激活指定发布，使其成为公开运行时的当前生效发布。
     *
     * <p>副作用：目标发布状态由 VERIFIED 变为 PUBLISHED（并写入 published_at），
     * active_release 单例表被更新为该发布。整体在事务内执行，失败即回滚。
     *
     * @param releaseId 待激活发布的 UUID 字符串
     * @return 携带 releaseId 与激活后状态 PUBLISHED 的结果
     * @throws IllegalArgumentException releaseId 不是合法 UUID，或发布不是 VERIFIED 状态
     */
    public PublicReleaseActivationResult activate(String releaseId) {
        validateReleaseId(releaseId);
        return transactions.execute(status -> activateInTransaction(releaseId));
    }

    private PublicReleaseActivationResult activateInTransaction(String releaseId) {
        int updatedRows = jdbcTemplate.update(ACTIVATE_RELEASE_SQL, releaseId);
        if (updatedRows != 1) {
            throw new IllegalStateException("only a VERIFIED release may be activated");
        }
        return new PublicReleaseActivationResult(releaseId, "PUBLISHED");
    }

    private void validateReleaseId(String releaseId) {
        try {
            UUID.fromString(releaseId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("release ID must be a UUID", exception);
        }
    }
}
