package com.portfolio.agent.portfolio.exception;

import com.portfolio.agent.common.exception.ApplicationException;

public final class ProjectNotFoundException extends ApplicationException {

    public ProjectNotFoundException(String slug) {
        super(PortfolioErrorCode.PROJECT_NOT_FOUND, "公开项目不存在: " + slug);
    }
}
