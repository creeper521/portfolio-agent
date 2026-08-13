package com.portfolio.agent.answer.context.adapter.postgres;

import org.springframework.boot.context.properties.ConfigurationProperties;

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

    public void validate() {
        require(url, "PORTFOLIO_CONTEXT_DATABASE_URL");
        require(username, "PORTFOLIO_CONTEXT_DATABASE_USERNAME");
        require(password, "PORTFOLIO_CONTEXT_DATABASE_PASSWORD");
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalStateException("PORTFOLIO_CONTEXT_DATABASE_SCHEMA is invalid");
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
    }
}
