package com.portfolio.agent.common.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.event.KeyValuePair;

public final class BrowserOriginEventFilter extends Filter<ILoggingEvent> {

    private static final String BROWSER_ORIGIN_KEY = "event.origin";
    private static final String BROWSER_ORIGIN_VALUE = "browser";

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event == null || event.getKeyValuePairs() == null) {
            return FilterReply.NEUTRAL;
        }
        for (KeyValuePair pair : event.getKeyValuePairs()) {
            if (BROWSER_ORIGIN_KEY.equals(pair.key)
                    && BROWSER_ORIGIN_VALUE.equals(pair.value)) {
                return FilterReply.DENY;
            }
        }
        return FilterReply.NEUTRAL;
    }
}
