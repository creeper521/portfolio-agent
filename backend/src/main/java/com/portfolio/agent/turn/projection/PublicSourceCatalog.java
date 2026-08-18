package com.portfolio.agent.turn.projection;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;

public final class PublicSourceCatalog {
    private final List<Source> sources;

    public PublicSourceCatalog(List<Source> sources) {
        this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (this.sources.stream().map(Source::getKey).distinct().count() != this.sources.size()) {
            throw new IllegalArgumentException("source keys must be unique");
        }
    }
    public List<Source> getSources() { return sources; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Source {
        private final String key;
        private final String code;
        private final String label;
        private final String type;
        private final String route;

        public Source(String key, String code, String label, String type, String route) {
            this.key = text(key, "key");
            this.code = code == null ? null : text(code, "code");
            this.label = text(label, "label");
            this.type = type == null ? null : text(type, "type");
            this.route = route(route);
        }
        public String getKey() { return key; }
        public String getCode() { return code; }
        public String getLabel() { return label; }
        public String getType() { return type; }
        public String getRoute() { return route; }

        private static String text(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
            return value.trim();
        }
        private static String route(String value) {
            String route = text(value, "route");
            if (!route.startsWith("/") || route.startsWith("//") || route.contains(":")
                    || route.contains("\\") || route.contains("..") || route.contains("\n")) {
                throw new IllegalArgumentException("route must be a public relative route");
            }
            return route;
        }
    }
}
