package com.portfolio.agent.ingestion.adapter.postgres;

import com.portfolio.agent.ingestion.gateway.DocumentEmbeddingPort;
import com.portfolio.agent.ingestion.gateway.MarkdownGovernanceStore;
import com.portfolio.agent.ingestion.service.MarkdownImportService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 治理库 PostgreSQL 装配配置：为私有治理数据导入公开投影这一显式运维能力单独构建
 * DataSource、Flyway、JdbcTemplate 与事务模板，并组装导入服务。
 *
 * <p>仅在 {@code portfolio.database.governance.enabled=true} 时生效，未配置时整套治理库
 * Bean 均不创建，运行时公开读取路径完全不受影响。治理库与运行时库是两个独立数据库，
 * Flyway 使用独立的 {@code flyway_schema_history_governance} 历史表，脚本位于
 * {@code classpath:db/governance}，不会与运行时迁移相互干扰。
 */
@Configuration
@ConditionalOnProperty(prefix = "portfolio.database.governance", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GovernanceDatabaseProperties.class)
public class GovernanceDatabaseConfiguration {

    /**
     * 创建治理库 HikariCP 连接池。
     *
     * <p>先执行 {@link GovernanceDatabaseProperties#validate()} 做启动期 fail-fast 校验，
     * 缺少连接配置时直接抛 {@link IllegalStateException}，避免带病启动。
     * 池上限固定为 3：导入是低并发的显式运维操作，无需更大的池。
     * 连接池在上下文关闭时自动释放（destroyMethod = "close"）。
     */
    @Bean(name = "governanceDataSource", destroyMethod = "close")
    DataSource governanceDataSource(GovernanceDatabaseProperties properties) {
        properties.validate();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setPoolName("portfolio-governance");
        config.setMaximumPoolSize(3);
        config.setReadOnly(false);
        return new HikariDataSource(config);
    }

    /**
     * 创建治理库 Flyway 实例并立即执行 {@code classpath:db/governance} 下的迁移。
     *
     * <p>迁移历史写入独立的 {@code flyway_schema_history_governance} 表，与运行时库的
     * Flyway 历史表隔离。该方法有数据库写副作用：Bean 创建即触发建表/变更。
     */
    @Bean(name = "governanceFlyway")
    Flyway governanceFlyway(@Qualifier("governanceDataSource") DataSource dataSource) {
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/governance")
                .table("flyway_schema_history_governance").load();
        flyway.migrate();
        return flyway;
    }

    /** 基于治理库 DataSource 创建 JdbcTemplate；参数中声明 Flyway 以确保迁移先于模板可用。 */
    @Bean(name = "governanceJdbcTemplate")
    JdbcTemplate governanceJdbcTemplate(
            @Qualifier("governanceDataSource") DataSource dataSource,
            @Qualifier("governanceFlyway") Flyway flyway) {
        return new JdbcTemplate(dataSource);
    }

    /** 创建治理库专用的事务管理器，与运行时库事务管理器互不共享连接。 */
    @Bean(name = "governanceTransactionManager")
    PlatformTransactionManager governanceTransactionManager(
            @Qualifier("governanceDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /** 创建治理库编程式事务模板，供导入服务在一次事务内完成写入与状态翻转。 */
    @Bean(name = "governanceTransactionTemplate")
    TransactionTemplate governanceTransactionTemplate(
            @Qualifier("governanceTransactionManager") PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    /** 组装治理库 {@link MarkdownGovernanceStore} 的 PostgreSQL 实现（源文档仓储）。 */
    @Bean(name = "governanceMarkdownGovernanceStore")
    MarkdownGovernanceStore governanceMarkdownGovernanceStore(
            @Qualifier("governanceJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new PostgresSourceDocumentRepository(jdbcTemplate);
    }

    /**
     * 组装治理库 Markdown 导入服务，注入存储、向量化端口与事务模板。
     * 该服务只在治理库启用时存在，是导入 CLI 的直接执行入口。
     */
    @Bean(name = "governanceMarkdownImportService")
    MarkdownImportService governanceMarkdownImportService(
            @Qualifier("governanceMarkdownGovernanceStore") MarkdownGovernanceStore store,
            @Qualifier("governanceDocumentEmbeddingPort") DocumentEmbeddingPort embeddingPort,
            @Qualifier("governanceTransactionTemplate") TransactionTemplate transactions) {
        return new MarkdownImportService(store, embeddingPort, transactions);
    }
}
