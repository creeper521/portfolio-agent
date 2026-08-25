package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres;

import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalAttemptFailure;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;

import java.sql.SQLTimeoutException;

/**
 * PostgreSQL 检索故障分类器（静态工具）：把 Spring/SQL 异常链归入 {@link RetrievalAttemptFailure}。
 *
 * <p>分类沿异常 cause 链逐层匹配，超时优先于连接故障；无法识别的异常一律归为
 * INTEGRITY_FAILURE，保证失败总是显式分类而不是被吞掉（fail-closed）。
 */
public final class PostgresRetrievalFailureClassifier {

    private PostgresRetrievalFailureClassifier() {
    }

    /**
     * 归类一个检索失败异常。
     *
     * @param failure 数据访问层抛出的异常（可为包装异常）
     * @return 机器可读失败分类；未知异常映射为 INTEGRITY_FAILURE
     */
    public static RetrievalAttemptFailure classify(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof QueryTimeoutException
                    || current instanceof SQLTimeoutException) {
                return RetrievalAttemptFailure.BACKEND_TIMEOUT;
            }
            if (current instanceof InvalidDataAccessResourceUsageException) {
                return RetrievalAttemptFailure.INTEGRITY_FAILURE;
            }
            if (current instanceof DataIntegrityViolationException) {
                return RetrievalAttemptFailure.INTEGRITY_FAILURE;
            }
            if (current instanceof DataAccessResourceFailureException
                    || current instanceof TransientDataAccessResourceException) {
                return RetrievalAttemptFailure.BACKEND_CONNECTION_UNAVAILABLE;
            }
            current = current.getCause();
        }
        // 兜底：未知故障按内容完整性失败处理，宁可保守失败也不静默继续
        return RetrievalAttemptFailure.INTEGRITY_FAILURE;
    }
}
