package com.portfolio.agent.ingestion.adapter.postgres;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 治理库连接配置项（前缀 {@code portfolio.database.governance}）。
 *
 * <p>持有治理 PostgreSQL 的 JDBC 地址与凭据，供
 * {@link GovernanceDatabaseConfiguration} 在治理库启用时创建连接池。
 * 该配置只服务私有治理导入这一显式运维能力，运行时公开读取不使用它。
 */
@ConfigurationProperties(prefix = "portfolio.database.governance")
public class GovernanceDatabaseProperties {

    private boolean enabled;
    private String url;
    private String username;
    private String password;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    /**
     * 启用期完整性校验：治理库开启时 url、username、password 三项均不得为空。
     *
     * @throws IllegalStateException 任一必填项缺失时抛出，异常消息指向对应的环境变量，
     *                               用于连接池创建前的启动期 fail-fast
     */
    public void validate() {
        if (!enabled) { return; }
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("PORTFOLIO_GOVERNANCE_DATABASE_URL is required");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("PORTFOLIO_GOVERNANCE_DATABASE_USERNAME is required");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD is required");
        }
    }
}
