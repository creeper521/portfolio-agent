package com.portfolio.agent.common.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

@Component
public final class FrontendDiagnosticAdmissionGate {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_SOURCES = 10_000;

    private final Clock clock;
    private final int eventsPerMinute;
    private final Map<String, SourceWindow> windows = new HashMap<>();

    @Autowired
    public FrontendDiagnosticAdmissionGate(FrontendDiagnosticProperties properties) {
        this(Clock.systemUTC(), properties.getFrontendEventsPerMinute());
    }

    public FrontendDiagnosticAdmissionGate(Clock clock, int eventsPerMinute) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (eventsPerMinute < 1) {
            throw new IllegalArgumentException("eventsPerMinute must be positive");
        }
        this.eventsPerMinute = eventsPerMinute;
    }

    public boolean tryAdmit(String sourceHash, int eventCount) {
        if (sourceHash == null
                || sourceHash.isBlank()
                || eventCount < 1
                || eventCount > eventsPerMinute) {
            return false;
        }
        Instant now = clock.instant();
        synchronized (windows) {
            SourceWindow window = windows.get(sourceHash);
            if (window == null) {
                if (windows.size() >= MAX_TRACKED_SOURCES) {
                    removeExpiredWindows(now);
                }
                if (windows.size() >= MAX_TRACKED_SOURCES) {
                    return false;
                }
                window = new SourceWindow(now);
                windows.put(sourceHash, window);
            } else if (!now.isBefore(window.startedAt.plus(WINDOW))) {
                window.startedAt = now;
                window.eventCount = 0;
            }
            if (window.eventCount > eventsPerMinute - eventCount) {
                return false;
            }
            window.eventCount += eventCount;
            return true;
        }
    }

    private void removeExpiredWindows(Instant now) {
        Iterator<Map.Entry<String, SourceWindow>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SourceWindow> entry = iterator.next();
            if (!now.isBefore(entry.getValue().startedAt.plus(WINDOW))) {
                iterator.remove();
            }
        }
    }

    private static final class SourceWindow {

        private Instant startedAt;
        private int eventCount;

        private SourceWindow(Instant startedAt) {
            this.startedAt = startedAt;
        }
    }
}
