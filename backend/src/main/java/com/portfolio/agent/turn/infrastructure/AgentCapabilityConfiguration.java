package com.portfolio.agent.turn.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.configuration.ModelOperationProperties;
import com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicy;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicyRegistry;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.ApplicationStartupDiagnostics;
import com.portfolio.agent.common.observability.AnonymousSourceHasher;
import com.portfolio.agent.common.web.ClientAddressResolver;
import com.portfolio.agent.turn.api.AgentRequestAdmissionGate;
import com.portfolio.agent.turn.capability.general.GeneralDraftCodec;
import com.portfolio.agent.turn.capability.general.GeneralDraftValidator;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeGenerator;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeModelPort;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeUnavailableException;
import com.portfolio.agent.turn.capability.general.GeneralPresentationComposer;
import com.portfolio.agent.turn.capability.general.GeneralTaskExecutor;
import com.portfolio.agent.turn.capability.synthesis.CrossDomainPresentationComposer;
import com.portfolio.agent.turn.capability.synthesis.CrossDomainTaskExecutor;
import com.portfolio.agent.turn.execution.SemanticTurnEngine;
import com.portfolio.agent.turn.infrastructure.model.GoalInterpretationAdapter;
import com.portfolio.agent.turn.infrastructure.model.OpenAiCompatibleGeneralKnowledgeAdapter;
import com.portfolio.agent.infrastructure.model.StructuredModelTransport;
import com.portfolio.agent.infrastructure.model.OpenAiCompatibleStructuredModelTransport;
import com.portfolio.agent.infrastructure.model.ModelExecutionResolver;
import com.portfolio.agent.infrastructure.model.SystemPromptCatalog;
import com.portfolio.agent.infrastructure.model.configuration.ConfiguredModelCatalog;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogSnapshot;
import com.portfolio.agent.turn.lifecycle.AgentTurnLifecycleService;
import com.portfolio.agent.turn.lifecycle.ActiveTurnCapacity;
import com.portfolio.agent.turn.lifecycle.RequestFingerprintFactory;
import com.portfolio.agent.turn.lifecycle.TurnExecutionStore;
import com.portfolio.agent.turn.lifecycle.AgentStateStore;
import com.portfolio.agent.turn.continuation.ContextMutationPlanner;
import com.portfolio.agent.turn.continuation.ConversationSessionResolver;
import com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.projection.PublicAgentTurnProjector;
import com.portfolio.agent.turn.planning.GoalBoundaryPolicy;
import com.portfolio.agent.turn.planning.GoalInterpretationInputFactory;
import com.portfolio.agent.turn.planning.GoalInterpretationPort;
import com.portfolio.agent.turn.planning.GoalInterpretationUnavailableException;
import com.portfolio.agent.turn.planning.GoalProposalCodec;
import com.portfolio.agent.turn.planning.GoalResolver;
import com.portfolio.agent.turn.planning.SafeConversationalFastPath;
import com.portfolio.agent.turn.planning.UnresolvedIntentPolicy;
import com.portfolio.agent.turn.planning.SemanticRouteValidator;
import com.portfolio.agent.turn.planning.PortfolioReviewedGoalSource;
import com.portfolio.agent.turn.planning.SemanticPlanCompiler;
import com.portfolio.agent.turn.planning.SemanticPlanValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent 能力的唯一生产装配：模型运行时、准入组件、生命周期服务与三种任务执行器。
 *
 * <p>装配次序体现三道 fail-closed 启动权威——model-runtime、各操作策略的
 * schema 门禁（经 AgentRuntimeReadiness）与会话上下文模式；Goal 解析与通用知识
 * 端口在对应操作不可用时装配为始终抛出不可用异常的闭门实现，而不是省略装配。
 * 密钥缺失时用随机临时密钥（仅限本地进程内 IN_MEMORY 语义）。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        ModelOperationProperties.class,
        AgentRuntimeProperties.class,
        com.portfolio.agent.turn.state.configuration.ConversationContextProperties.class
})
public class AgentCapabilityConfiguration {
    /** 系统提示词目录（固定文案资源）。 */
    @Bean
    SystemPromptCatalog systemPromptCatalog() {
        return new SystemPromptCatalog();
    }

    /** 启动诊断事件：记录模型运行时开关、目录规模、检索档位与限流参数等固定计数。 */
    @Bean
    ApplicationStartupDiagnostics applicationStartupDiagnostics(
            DiagnosticEventPublisher diagnosticEventPublisher,
            com.portfolio.agent.infrastructure.model.configuration.ModelRuntimeProperties
                    modelRuntimeProperties,
            ModelCatalogSnapshot modelCatalog,
            @org.springframework.beans.factory.annotation.Value(
                    "${portfolio.retrieval.profile:DISABLED}") String retrievalProfile,
            AgentRuntimeProperties runtimeProperties) {
        return new ApplicationStartupDiagnostics(
                diagnosticEventPublisher,
                modelRuntimeProperties.isEnabled(),
                modelCatalog.getEntries().size(),
                retrievalProfile,
                runtimeProperties.getTurnTimeout().toMillis(),
                runtimeProperties.getRequestsPerMinute(),
                runtimeProperties.getMaxConcurrentPerSource());
    }

    /** State 执行线程池：虚拟线程 per-task，容器关闭时关闭。 */
    @Bean(destroyMethod = "close")
    ExecutorService conversationRequestExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /** 客户端地址解析器：仅在配置信任的代理链下采纳转发头。 */
    @Bean
    ClientAddressResolver clientAddressResolver(AgentRuntimeProperties properties) {
        return new ClientAddressResolver(
                properties.isTrustProxy(), properties.getTrustedProxies());
    }

    /** 匿名来源哈希器：进程级密钥把地址转换为不可逆哈希。 */
    @Bean
    AnonymousSourceHasher anonymousSourceHasher() {
        return new AnonymousSourceHasher();
    }

    /** 来源级准入闸（RPM + 并发 + 来源容量）。 */
    @Bean
    AgentRequestAdmissionGate agentRequestAdmissionGate(AgentRuntimeProperties properties) {
        return new AgentRequestAdmissionGate(
                java.time.Clock.systemUTC(),
                properties.getRequestsPerMinute(),
                properties.getMaxConcurrentPerSource(),
                properties.getMaxTrackedSources());
    }

    /** 全局 Active Turn 容量边界。 */
    @Bean
    ActiveTurnCapacity activeTurnCapacity(AgentRuntimeProperties properties) {
        return new ActiveTurnCapacity(properties.getMaxActiveTurns());
    }

    /** 模型操作策略注册表（每个操作的 schema/输出预算/超时权威）。 */
    @Bean
    ModelOperationPolicyRegistry modelOperationPolicyRegistry(ModelOperationProperties properties) {
        return properties.toRegistry();
    }

    /** 运行时就绪度：会话模式 × 操作策略的启动期判定。 */
    @Bean
    AgentRuntimeReadiness agentRuntimeReadiness(
            com.portfolio.agent.turn.state.configuration.ConversationContextProperties
                    contextProperties,
            ModelOperationPolicyRegistry operationPolicies,
            com.portfolio.agent.infrastructure.model.structured
                    .StructuredOutputContractRegistry contracts) {
        return new AgentRuntimeReadiness(
                contextProperties.getMode(), operationPolicies, contracts);
    }

    /** OpenAI 兼容结构化传输：连接与请求超时取各操作策略中的最大超时。 */
    @Bean
    StructuredModelTransport structuredModelTransport(
            ObjectMapper mapper,
            ModelOperationPolicyRegistry operationPolicies,
            DiagnosticEventPublisher diagnostics,
            com.portfolio.agent.infrastructure.model.structured
                    .StructuredOutputContractRegistry contracts) {
        Duration transportTimeout = maximumTransportTimeout(operationPolicies);
        return new OpenAiCompatibleStructuredModelTransport(
                HttpClient.newBuilder()
                        .connectTimeout(transportTimeout)
                        .build(),
                mapper, transportTimeout, diagnostics, contracts);
    }

    @Bean
    com.portfolio.agent.infrastructure.model.structured.StructuredOutputGateway
            structuredOutputGateway(
                    StructuredModelTransport transport,
                    com.portfolio.agent.infrastructure.model.structured
                            .StructuredOutputContractRegistry contracts) {
        return new com.portfolio.agent.infrastructure.model.structured
                .StructuredOutputGateway(transport, contracts);
    }

    /** 模型执行解析器：目录快照 + 配置绑定，Claim 后冻结无隐式回退。 */
    @Bean
    ModelExecutionResolver modelExecutionResolver(
            ModelCatalogSnapshot catalog,
            ConfiguredModelCatalog configuredCatalog) {
        return new ModelExecutionResolver(
                catalog, configuredCatalog::getRequiredBinding);
    }

    /**
     * Goal 解析端口：操作不可用时装配为闭门实现（恒抛不可用异常），
     * 可用时装配为带 schema codec、输出预算与超时的适配器。
     */
    @Bean
    GoalInterpretationPort goalInterpretationPort(
            ObjectMapper objectMapper,
            ModelOperationPolicyRegistry operationPolicies,
            com.portfolio.agent.infrastructure.model.structured
                    .StructuredOutputGateway gateway,
            SystemPromptCatalog prompts,
            AgentRuntimeReadiness readiness,
            DiagnosticEventPublisher diagnostics) {
        if (!readiness.isOperationAvailable(ModelOperation.TURN_INTERPRETATION)) {
            return (input, deadline, modelExecution) -> {
                throw new GoalInterpretationUnavailableException();
            };
        }
        ModelOperationPolicy operation = operationPolicies.get(
                ModelOperation.TURN_INTERPRETATION);
        return new GoalInterpretationAdapter(
                gateway, objectMapper, new GoalProposalCodec(),
                prompts.goalInterpretation(),
                operation.getMaxOutputTokens(), operation.getTimeout(),
                new com.portfolio.agent.common.observability.ModelOutputDiagnostics(diagnostics));
    }

    /** 通用知识模型端口：同样按就绪度在闭门实现与适配器之间二选一。 */
    @Bean
    GeneralKnowledgeModelPort generalKnowledgeModelPort(
            ObjectMapper objectMapper,
            ModelOperationPolicyRegistry operationPolicies,
            com.portfolio.agent.infrastructure.model.structured
                    .StructuredOutputGateway gateway,
            SystemPromptCatalog prompts,
            AgentRuntimeReadiness readiness,
            DiagnosticEventPublisher diagnostics) {
        if (!readiness.isOperationAvailable(ModelOperation.GENERAL_KNOWLEDGE)) {
            return (request, modelExecution) -> {
                throw new GeneralKnowledgeUnavailableException(
                        "general capability is unavailable");
            };
        }
        ModelOperationPolicy operation = operationPolicies.get(
                ModelOperation.GENERAL_KNOWLEDGE);
        return new OpenAiCompatibleGeneralKnowledgeAdapter(
                gateway, objectMapper, prompts.generalKnowledge(),
                operation.getMaxOutputTokens(), operation.getTimeout(),
                prompts.generalProviderDraft(),
                new com.portfolio.agent.common.observability
                        .ModelOutputDiagnostics(diagnostics));
    }

    /** 通用知识任务执行器：生成器（codec + 校验）+ 呈现组合器。 */
    @Bean
    GeneralTaskExecutor generalTaskExecutor(
            ObjectMapper objectMapper,
            GeneralKnowledgeModelPort modelPort,
            DiagnosticEventPublisher diagnostics) {
        return new GeneralTaskExecutor(
                new GeneralKnowledgeGenerator(
                        modelPort, new GeneralDraftCodec(objectMapper),
                        new GeneralDraftValidator(),
                        new com.portfolio.agent.common.observability.ModelOutputDiagnostics(diagnostics)),
                new GeneralPresentationComposer());
    }

    /** 跨域综合任务执行器。 */
    @Bean
    CrossDomainTaskExecutor crossDomainTaskExecutor() {
        return new CrossDomainTaskExecutor(new CrossDomainPresentationComposer());
    }

    /** 语义执行引擎：三种任务执行器 + State 线程池 + 并发上限。 */
    @Bean
    SemanticTurnEngine semanticTurnEngine(
            com.portfolio.agent.turn.capability.portfolio.PortfolioTaskExecutor portfolioTaskExecutor,
            GeneralTaskExecutor generalTaskExecutor,
            CrossDomainTaskExecutor crossDomainTaskExecutor,
            java.util.concurrent.ExecutorService conversationRequestExecutor) {
        return new SemanticTurnEngine(
                List.of(portfolioTaskExecutor, generalTaskExecutor, crossDomainTaskExecutor),
                conversationRequestExecutor, 4);
    }

    /** 零目标低信息策略：独立于问候 fast path 的封闭确定性规则。 */
    @Bean
    UnresolvedIntentPolicy unresolvedIntentPolicy() {
        return new UnresolvedIntentPolicy();
    }

    /** Goal 解析器：解释端口 + 已审阅主体来源 + 边界策略 + 模型输出诊断。 */
    @Bean
    GoalResolver goalResolver(
            PortfolioKnowledgeGateway knowledgeGateway,
            GoalInterpretationPort goalInterpretationPort,
            UnresolvedIntentPolicy unresolvedIntentPolicy,
            DiagnosticEventPublisher diagnostics) {
        return new GoalResolver(
                goalInterpretationPort, new PortfolioReviewedGoalSource(knowledgeGateway),
                new GoalInterpretationInputFactory(),
                new SafeConversationalFastPath(), unresolvedIntentPolicy,
                new SemanticRouteValidator(),
                new GoalBoundaryPolicy(),
                new com.portfolio.agent.common.observability.ModelOutputDiagnostics(diagnostics));
    }

    /** 语义计划编译器（含计划校验器）。 */
    @Bean SemanticPlanCompiler semanticPlanCompiler() {
        return new SemanticPlanCompiler(new SemanticPlanValidator());
    }

    /** IN_MEMORY 模式下的进程内 Agent State（快速测试与定向诊断）。 */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "portfolio.conversation-context", name = "mode", havingValue = "IN_MEMORY")
    AgentStateStore inMemoryTurnExecutionStore(
            com.portfolio.agent.turn.state.configuration.ConversationContextProperties properties,
            InMemoryConversationSessionStore sessionStore) {
        properties.validate();
        java.time.Clock clock = java.time.Clock.systemUTC();
        return new com.portfolio.agent.turn.state.memory.InMemoryTurnExecutionStore(
                new com.portfolio.agent.turn.continuation.ClarificationStore(
                        clock, properties.getClarificationTtl()),
                properties.getAbsoluteTtl(), sessionStore, clock);
    }

    /** DISABLED 模式（默认）下的 fail-closed State：所有操作抛不可用异常。 */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "portfolio.conversation-context", name = "mode",
            havingValue = "DISABLED", matchIfMissing = true)
    AgentStateStore unavailableTurnExecutionStore() {
        return new com.portfolio.agent.turn.state.UnavailableTurnExecutionStore();
    }

    /** IN_MEMORY 模式下的进程内会话存储。 */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "portfolio.conversation-context", name = "mode",
            havingValue = "IN_MEMORY")
    InMemoryConversationSessionStore inMemoryConversationSessionStore() {
        return new InMemoryConversationSessionStore();
    }

    /** DISABLED 模式下的会话存储：占位实现（State 不可用时不会被触达）。 */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "portfolio.conversation-context", name = "mode",
            havingValue = "DISABLED", matchIfMissing = true)
    ConversationSessionStore unavailableConversationSessionStore() {
        return new InMemoryConversationSessionStore();
    }

    @Bean
    AgentTurnLifecycleService agentTurnLifecycleService(
            PortfolioKnowledgeGateway knowledgeGateway, GoalResolver goalResolver,
            SemanticPlanCompiler compiler, SemanticTurnEngine engine,
            AgentStateStore store, ConversationSessionStore sessionStore,
            com.portfolio.agent.turn.state.configuration.ConversationContextProperties properties,
            AgentRuntimeProperties runtimeProperties,
            ModelExecutionResolver modelExecutionResolver,
            ExecutorService conversationRequestExecutor) {
        byte[] configuredTokenKey = decodeOrRandom(properties.getCrypto().getCurrentTokenKey());
        java.util.List<byte[]> previousTokenKeys =
                properties.getCrypto().getPreviousTokenKey() == null
                        || properties.getCrypto().getPreviousTokenKey().isBlank()
                        ? java.util.List.of()
                        : java.util.List.of(decodeConfigured(
                        properties.getCrypto().getPreviousTokenKey()));
        byte[] fingerprintSecret = configuredTokenKey;
        byte[] sessionSecret = configuredTokenKey;
        String fingerprintKeyId = properties.getCrypto().getCurrentTokenKeyId() == null
                || properties.getCrypto().getCurrentTokenKeyId().isBlank()
                ? "ephemeral-token" : properties.getCrypto().getCurrentTokenKeyId().trim();
        java.util.Map<String, byte[]> previousFingerprintKeys =
                previousTokenKeys.isEmpty() ? java.util.Map.of()
                        : java.util.Map.of(
                        properties.getCrypto().getPreviousTokenKeyId().trim(),
                        previousTokenKeys.getFirst());
        java.time.Clock clock = java.time.Clock.systemUTC();
        ContextMutationPlanner planner = new ContextMutationPlanner(() -> {
            byte[] value = new byte[24];
            new java.security.SecureRandom().nextBytes(value);
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        });
        return new AgentTurnLifecycleService(
                knowledgeGateway, goalResolver, compiler, engine,
                new PublicAgentTurnProjector(), planner, store,
                new RequestFingerprintFactory(
                        fingerprintKeyId, fingerprintSecret, previousFingerprintKeys),
                new ConversationSessionResolver(
                        sessionStore, sessionSecret, previousTokenKeys,
                        clock, properties.getAbsoluteTtl()),
                conversationRequestExecutor,
                clock, runtimeProperties.getLeaseDuration(),
                runtimeProperties.getTurnTimeout(),
                runtimeProperties.getSettlementReserve(),
                properties.getDiscussionTtl(), modelExecutionResolver);
    }

    /** 生成 32 字节随机密钥（未配置显式密钥时的临时进程内密钥）。 */
    private byte[] randomSecret() {
        byte[] value = new byte[32];
        new java.security.SecureRandom().nextBytes(value);
        return value;
    }

    /** 取各操作策略中的最大超时作为传输层连接/请求超时。 */
    private Duration maximumTransportTimeout(
            ModelOperationPolicyRegistry operationPolicies) {
        Duration maximum = Duration.ofSeconds(1);
        for (ModelOperationPolicy policy : operationPolicies.asMap().values()) {
            Duration timeout = policy.getTimeout();
            if (timeout != null && timeout.compareTo(maximum) > 0) {
                maximum = timeout;
            }
        }
        return maximum;
    }

    /** 解码配置密钥；缺失或空白时生成随机临时密钥；长度不足 32 字节即启动失败。 */
    private byte[] decodeOrRandom(String encoded) {
        if (encoded == null || encoded.isBlank()) return randomSecret();
        try {
            byte[] value = java.util.Base64.getDecoder().decode(encoded.trim());
            if (value.length < 32) throw new IllegalStateException("Agent token key is too short");
            return value;
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("Agent token key must be base64", failure);
        }
    }

    /** 解码必填的配置密钥（previous 密钥路径）：缺失即启动失败，不回退随机值。 */
    private byte[] decodeConfigured(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("Agent token key is required");
        }
        return decodeOrRandom(encoded);
    }
}
