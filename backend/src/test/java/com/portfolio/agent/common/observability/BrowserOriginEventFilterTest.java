package com.portfolio.agent.common.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserOriginEventFilterTest {

    private static final String TEST_LOGGER_NAME = "browser-origin-event-filter-test";

    @Test
    void deniesEventsCarryingTheBrowserOriginKeyValue() {
        Filter<ILoggingEvent> filter = new BrowserOriginEventFilter();
        filter.start();

        assertThat(filter.decide(loggedEvent("frontend.agent.request.completed", "browser")))
                .isEqualTo(ch.qos.logback.core.spi.FilterReply.DENY);
    }

    @Test
    void allowsEventsWithoutTheBrowserOriginKeyValue() {
        Filter<ILoggingEvent> filter = new BrowserOriginEventFilter();
        filter.start();

        assertThat(filter.decide(loggedEvent("http.request.completed", "server")))
                .isEqualTo(ch.qos.logback.core.spi.FilterReply.NEUTRAL);
    }

    @Test
    void allowsEventsWithoutAnyKeyValuePairs() {
        Filter<ILoggingEvent> filter = new BrowserOriginEventFilter();
        filter.start();

        Logger logger = (Logger) LoggerFactory.getLogger(TEST_LOGGER_NAME + "-plain");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            logger.info("plain backend line");
            assertThat(filter.decide(appender.list.get(0)))
                    .isEqualTo(ch.qos.logback.core.spi.FilterReply.NEUTRAL);
        } finally {
            logger.detachAppender(appender);
        }
    }

    private ILoggingEvent loggedEvent(String message, String origin) {
        Logger logger = (Logger) LoggerFactory.getLogger(TEST_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            logger.atInfo().addKeyValue("event.origin", origin).log(message);
            return appender.list.get(0);
        } finally {
            logger.detachAppender(appender);
        }
    }
}
