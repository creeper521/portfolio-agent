package com.portfolio.agent.portfolio.controller;

import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogSnapshot;
import com.portfolio.agent.portfolio.dto.response.AgentAvailabilityResponse;
import com.portfolio.agent.portfolio.dto.response.PortfolioSnapshotResponse;
import com.portfolio.agent.portfolio.mapper.PortfolioResponseMapper;
import com.portfolio.agent.portfolio.service.PortfolioService;
import com.portfolio.agent.turn.infrastructure.AgentRuntimeReadiness;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作品集公开内容的只读 HTTP 入口。
 *
 * <p>绑定 {@code GET /api/portfolio}，把 {@link PortfolioService} 聚合的公开内容经
 * {@link PortfolioResponseMapper} 映射为对外响应，并在启动时固化 Agent 可用性描述。
 * 响应统一使用 {@code Cache-Control: no-store}，避免任何代理缓存公开快照。
 */
@RestController
@RequestMapping("/api/portfolio")
public final class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioResponseMapper responseMapper;
    private final AgentAvailabilityResponse agentAvailability;

    /**
     * 注入聚合服务与响应映射器，并把当前 Agent 运行时就绪状态转换为不可变的可用性描述。
     *
     * <p>可用性在构造时确定：Agent 整体不可用时直接返回不可用；否则按
     * TURN_INTERPRETATION 操作是否可用决定自由文本语义路由是 AVAILABLE 还是 DISABLED。
     * 该描述随后被静态复用，不随后续运行状态波动而变化。
     */
    public PortfolioController(
            PortfolioService portfolioService,
            PortfolioResponseMapper responseMapper,
            AgentRuntimeReadiness readiness,
            ModelCatalogSnapshot modelCatalog
    ) {
        this.portfolioService = portfolioService;
        this.responseMapper = responseMapper;
        this.agentAvailability = !readiness.isAgentAvailable()
                ? AgentAvailabilityResponse.unavailable(modelCatalog)
                : AgentAvailabilityResponse.available(
                        readiness.isOperationAvailable(ModelOperation.TURN_INTERPRETATION)
                                ? AgentAvailabilityResponse.FreeTextSemanticRouting.AVAILABLE
                                : AgentAvailabilityResponse.FreeTextSemanticRouting.DISABLED,
                        modelCatalog);
    }

    /**
     * 返回完整作品集公开快照。
     *
     * <p>聚合结果包含项目、案例、声明、证据与预设问题等公开内容，附带 Agent 可用性信息。
     * 响应体不可缓存（no-store）。
     *
     * @return 200 与公开快照响应；本资源不做版本化，快照版本信息内嵌在响应体内
     */
    @GetMapping
    public ResponseEntity<PortfolioSnapshotResponse> getPortfolioSnapshot() {
        PortfolioSnapshotResponse response = responseMapper.toPortfolioSnapshotResponse(
                portfolioService.getPublicContent(), agentAvailability);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
