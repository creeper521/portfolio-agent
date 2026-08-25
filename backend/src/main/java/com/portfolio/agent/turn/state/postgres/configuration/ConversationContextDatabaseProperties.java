package com.portfolio.agent.turn.state.postgres.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent State 专用数据库连接配置（portfolio.database.context 前缀）。
 *
 * <p>凭据只经环境变量注入，不进入仓库；schema 固定为 agent_context（专用隔离
 * schema），validate 在数据源创建前强制校验。</p>
 */
@ConfigurationProperties(prefix = "portfolio.database.context")
public class ConversationContextDatabaseProperties {
    private String url;
    private String username;
    private String password;
    private String schema = "agent_context";

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    /**
     * fail-closed 校验：URL/用户名/密码必填（缺失即启动失败），
     * schema 必须固定为 agent_context。
     */
    public void validate() {
        require(url, "PORTFOLIO_CONTEXT_DATABASE_URL");
        require(username, "PORTFOLIO_CONTEXT_DATABASE_USERNAME");
        require(password, "PORTFOLIO_CONTEXT_DATABASE_PASSWORD");
        if (!"agent_context".equals(schema)) {
            throw new IllegalStateException(
                    "PORTFOLIO_CONTEXT_DATABASE_SCHEMA must be agent_context");
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
    }
}
