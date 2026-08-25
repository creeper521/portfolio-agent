package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 展示快照：发布包 presentation 文件反序列化后的前端展示配置。
 *
 * <p>audiences/homeSections/metrics/explorePortals/footer 均为原样保留的 JSON 节点
 * （{@code JsonNode}），本类不解析其内部结构，仅保证集合不可变并在构造与读取
 * footer 时做深拷贝，防止外部修改 JsonNode 破坏快照内容。
 */
public final class PresentationSnapshot {
    private final String schemaVersion;
    private final String contentVersion;
    private final List<JsonNode> audiences;
    private final List<JsonNode> homeSections;
    private final List<JsonNode> metrics;
    private final List<JsonNode> explorePortals;
    private final JsonNode footer;

    @JsonCreator
    public PresentationSnapshot(@JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("contentVersion") String contentVersion,
            @JsonProperty("audiences") List<JsonNode> audiences,
            @JsonProperty("homeSections") List<JsonNode> homeSections,
            @JsonProperty("metrics") List<JsonNode> metrics,
            @JsonProperty("explorePortals") List<JsonNode> explorePortals,
            @JsonProperty("footer") JsonNode footer) {
        this.schemaVersion = schemaVersion; this.contentVersion = contentVersion;
        this.audiences = List.copyOf(audiences); this.homeSections = List.copyOf(homeSections);
        this.metrics = List.copyOf(metrics); this.explorePortals = List.copyOf(explorePortals);
        this.footer = footer.deepCopy();
    }
    public String getSchemaVersion() { return schemaVersion; }
    public String getContentVersion() { return contentVersion; }
    public List<JsonNode> getAudiences() { return audiences; }
    public List<JsonNode> getHomeSections() { return homeSections; }
    public List<JsonNode> getMetrics() { return metrics; }
    public List<JsonNode> getExplorePortals() { return explorePortals; }
    public JsonNode getFooter() { return footer.deepCopy(); }
}
