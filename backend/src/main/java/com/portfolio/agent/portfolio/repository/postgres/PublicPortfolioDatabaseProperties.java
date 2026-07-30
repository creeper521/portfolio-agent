package com.portfolio.agent.portfolio.repository.postgres;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portfolio.database.public")
public class PublicPortfolioDatabaseProperties {

    private boolean enabled;
    private String url;
    private String username;
    private String password;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void validate() {
        if (!enabled) {
            return;
        }
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("PORTFOLIO_PUBLIC_DATABASE_URL is required");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("PORTFOLIO_PUBLIC_DATABASE_USERNAME is required");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("PORTFOLIO_PUBLIC_DATABASE_PASSWORD is required");
        }
    }
}
