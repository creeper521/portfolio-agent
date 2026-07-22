# Portfolio Agent C3 Model Provider Registry Implementation Plan

> **Implementation status (2026-07-22):** Implemented and verified as `c3-model-registry-v1`. The unchecked checklist below is retained as the original implementation record; it is not current outstanding work. Other C3 capabilities remain unadmitted and unimplemented.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace repeated DeepSeek/GLM selection branches with one immutable, fail-closed, built-in Model Provider Registry while preserving the single-Provider answer and privacy contracts.

**Architecture:** Two immutable built-in descriptors are validated into `ModelProviderRegistrySnapshot`. `ModelPolicy` selects one compatible descriptor, `ModelProviderAdapterFactory` creates one OpenAI-compatible adapter, and `AgentExecutionSnapshot` records only the Registry version. There is no dynamic discovery, retry, or cross-Provider failover.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring `RestClient`, JUnit 5, AssertJ, MockRestServiceServer, Maven, PowerShell release gates.

## Global Constraints

- Preserve all existing modified and untracked files. Never reset, restore, checkout, delete, or overwrite unrelated work.
- Do not run `git add`, `git commit`, `git push`, or create a PR without fresh explicit authorization.
- No `var`, `record`, Lombok, field injection, credential literals, or sensitive `toString()` implementations.
- Keep the existing Answer API, four dimensions, structured sections, `ModelExpressionPort`, environment variable names, and whole-answer fallback behavior.
- Keep model expression default-disabled, one Provider per process, one non-streaming attempt, no retry, and no cross-Provider resend.
- Implement only Model Provider Registry. Do not add Tool Registry, Hook, Orchestrator, multi-Agent, DurableTask, persistent conversation, plugins, or Spring AI.
- Every behavior change follows RED → GREEN → REFACTOR. Do not stage or commit at task checkpoints.

## File Map

**Create:**

- `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ModelProviderCapability.java`
- `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ModelProviderDescriptor.java`
- `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ModelProviderRegistrySnapshot.java`
- `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ModelProviderAdapterFactory.java`
- `backend/src/main/java/com/portfolio/agent/answer/gateway/ModelProviderRegistry.java`
- Corresponding tests under `backend/src/test/java/com/portfolio/agent/answer/adapter/model/`.

**Modify:**

- `OpenAiCompatibleModelExpressionAdapter.java`, `ModelExpressionConfiguration.java`, `ModelExpressionProperties.java`
- `AgentExecutionSnapshot.java`, `AgentExecutionSnapshotFactory.java`, and direct-construction tests
- architecture/privacy checker scripts and tests
- README, SECURITY, documentation status index, and authoritative C design

---

### Task 1: Immutable Descriptor and Registry Snapshot

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ModelProviderCapability.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ModelProviderDescriptor.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ModelProviderRegistrySnapshot.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/gateway/ModelProviderRegistry.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/adapter/model/ModelProviderDescriptorTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/adapter/model/ModelProviderRegistrySnapshotTest.java`

**Interfaces:**

- `ModelProviderDescriptor(ModelProviderKind, String adapterVersion, URI, String modelName, Set<String> policyVersions, Set<String> schemaVersions, Set<ModelProviderCapability>)`
- `boolean ModelProviderDescriptor.supports(String modelPolicyVersion, String answerSchemaVersion)`
- `ModelProviderRegistry.getSnapshotVersion()`
- `ModelProviderRegistry.supports(ModelProviderKind, String modelPolicyVersion, String answerSchemaVersion)`
- Package-visible `ModelProviderRegistrySnapshot.getRequiredDescriptor(ModelProviderKind)` for model configuration only.

- [ ] **Step 1: Write failing Descriptor tests**

```java
@Test
void validatesNonSecretMetadataAndExactCompatibility() {
    ModelProviderDescriptor descriptor = new ModelProviderDescriptor(
            ModelProviderKind.DEEPSEEK_V4_FLASH,
            "c3-openai-compatible-v1",
            URI.create("https://api.deepseek.com/chat/completions"),
            "deepseek-v4-flash",
            Set.of("c1-policy-v1"),
            Set.of("c1.answer.v1"),
            Set.of(ModelProviderCapability.STRUCTURED_JSON_OUTPUT,
                    ModelProviderCapability.THINKING_CONTROL,
                    ModelProviderCapability.NON_STREAMING));

    assertThat(descriptor.supports("c1-policy-v1", "c1.answer.v1")).isTrue();
    assertThat(descriptor.supports("unknown", "c1.answer.v1")).isFalse();
    assertThat(ModelProviderDescriptor.class.getDeclaredFields())
            .extracting(Field::getName)
            .doesNotContain("apiKey", "secret", "token", "prompt", "request", "response");
    assertThat(ModelProviderDescriptor.class.getDeclaredMethods())
            .extracting(Method::getName).doesNotContain("toString");
}

@Test
void rejectsNonHttpsEndpointAndIncompleteCapabilities() {
    assertThatThrownBy(() -> new ModelProviderDescriptor(
            ModelProviderKind.DEEPSEEK_V4_FLASH,
            "adapter-v1",
            URI.create("http://api.deepseek.com/chat/completions"),
            "deepseek-v4-flash",
            Set.of("c1-policy-v1"),
            Set.of("c1.answer.v1"),
            Set.of()))
            .isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: Write failing Registry tests**

```java
@Test
void builtInRegistryContainsExactlyTheTwoReviewedProviders() {
    ModelProviderRegistrySnapshot registry = ModelProviderRegistrySnapshot.builtIn();

    assertThat(registry.getSnapshotVersion()).isEqualTo("c3-model-registry-v1");
    assertThat(registry.getProviderIds()).containsExactlyInAnyOrder(
            ModelProviderKind.DEEPSEEK_V4_FLASH,
            ModelProviderKind.GLM_4_7);
    assertThat(registry.supports(
            ModelProviderKind.GLM_4_7,
            "c1-policy-v1",
            "c1.answer.v1")).isTrue();
}

@Test
void duplicateProviderIdFailsInsteadOfFirstWins() {
    ModelProviderDescriptor first = deepSeekDescriptor("adapter-v1");
    ModelProviderDescriptor duplicate = deepSeekDescriptor("adapter-v2");

    assertThatThrownBy(() -> new ModelProviderRegistrySnapshot(
            "test-registry-v1", List.of(first, duplicate)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DEEPSEEK_V4_FLASH");
}
```

- [ ] **Step 3: Run tests and confirm RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend\pom.xml -Dtest=ModelProviderDescriptorTest,ModelProviderRegistrySnapshotTest test
```

Expected: compilation failure because the new types do not exist.

- [ ] **Step 4: Implement the closed capability and immutable Descriptor**

```java
public enum ModelProviderCapability {
    STRUCTURED_JSON_OUTPUT,
    THINKING_CONTROL,
    NON_STREAMING
}
```

```java
public final class ModelProviderDescriptor {
    private final ModelProviderKind providerId;
    private final String adapterVersion;
    private final URI endpoint;
    private final String modelName;
    private final Set<String> supportedModelPolicyVersions;
    private final Set<String> supportedAnswerSchemaVersions;
    private final Set<ModelProviderCapability> capabilities;

    public boolean supports(String policyVersion, String schemaVersion) {
        return supportedModelPolicyVersions.contains(policyVersion)
                && supportedAnswerSchemaVersions.contains(schemaVersion)
                && capabilities.containsAll(Set.of(
                        ModelProviderCapability.STRUCTURED_JSON_OUTPUT,
                        ModelProviderCapability.THINKING_CONTROL,
                        ModelProviderCapability.NON_STREAMING));
    }
}
```

Implement the full constructor and getters. Private helpers must reject null/blank values, non-HTTPS URI or blank host, empty sets, null elements, and incomplete capability declarations. Copy every set with `Set.copyOf`.

- [ ] **Step 5: Implement the gateway and immutable Registry**

```java
public interface ModelProviderRegistry {
    String getSnapshotVersion();

    boolean supports(
            ModelProviderKind provider,
            String modelPolicyVersion,
            String answerSchemaVersion);
}
```

```java
public final class ModelProviderRegistrySnapshot implements ModelProviderRegistry {
    public static final String BUILT_IN_VERSION = "c3-model-registry-v1";
    private final String snapshotVersion;
    private final Map<ModelProviderKind, ModelProviderDescriptor> descriptors;

    public static ModelProviderRegistrySnapshot builtIn() {
        Set<ModelProviderCapability> capabilities = Set.of(
                ModelProviderCapability.STRUCTURED_JSON_OUTPUT,
                ModelProviderCapability.THINKING_CONTROL,
                ModelProviderCapability.NON_STREAMING);
        return new ModelProviderRegistrySnapshot(BUILT_IN_VERSION, List.of(
                descriptor(ModelProviderKind.DEEPSEEK_V4_FLASH,
                        "https://api.deepseek.com/chat/completions",
                        "deepseek-v4-flash", capabilities),
                descriptor(ModelProviderKind.GLM_4_7,
                        "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                        "glm-4.7", capabilities)));
    }

    @Override
    public boolean supports(ModelProviderKind provider, String policy, String schema) {
        ModelProviderDescriptor descriptor = descriptors.get(provider);
        return descriptor != null && descriptor.supports(policy, schema);
    }
}
```

The constructor must populate an `EnumMap`, reject duplicate IDs before `Map.copyOf`, reject empty collections, and expose an immutable `getProviderIds()`. `getRequiredDescriptor` must throw for missing IDs and never select another Provider.

- [ ] **Step 6: Run tests and confirm GREEN**

Expected: both new test classes pass, then `git diff --check` passes. Do not stage.

---

### Task 2: Descriptor-Driven Adapter and Spring Selection

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ModelProviderAdapterFactory.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/adapter/model/ModelProviderAdapterFactoryTest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/OpenAiCompatibleModelExpressionAdapter.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ModelExpressionConfiguration.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ModelExpressionProperties.java`
- Modify: existing model adapter/configuration tests.

**Interfaces:**

- Adapter constructor replaces `ModelProviderKind provider` with `ModelProviderDescriptor descriptor`.
- Factory accepts only one selected Descriptor and one selected API key.
- Configuration publishes one Registry bean, one fail-closed ModelPolicy, and one ModelExpressionPort.

- [ ] **Step 1: Change tests before implementation**

Update `OpenAiCompatibleModelExpressionAdapterTest.fixture`:

```java
ModelProviderDescriptor descriptor = ModelProviderRegistrySnapshot.builtIn()
        .getRequiredDescriptor(provider);
OpenAiCompatibleModelExpressionAdapter adapter =
        new OpenAiCompatibleModelExpressionAdapter(
                builder, objectMapper, new ModelPromptFactory(objectMapper),
                descriptor, "test-key", 1200);
```

Add configuration tests:

```java
@Test
void incompatiblePolicyVersionDisablesModelExpression() {
    ModelExpressionProperties properties = approvedGlmProperties();
    properties.setModelPolicyVersion("unsupported-policy");

    ModelPolicy policy = configuration.modelPolicy(
            properties, configuration.modelProviderRegistry());

    assertThat(policy.isModelEnabled()).isFalse();
}

@Test
void selectedCredentialNeverFallsBackToAnotherProvider() {
    ModelExpressionProperties properties = new ModelExpressionProperties();
    properties.setProvider(ModelProviderKind.GLM_4_7);
    properties.setDeepseekApiKey("deepseek-only-key");

    assertThat(properties.apiKeyFor(ModelProviderKind.GLM_4_7)).isEmpty();
}
```

- [ ] **Step 2: Run all model adapter tests and confirm RED**

Expected: constructor, method, bean, and Factory symbols are missing.

- [ ] **Step 3: Make the HTTP Adapter Descriptor-driven**

```java
private final ModelProviderDescriptor descriptor;
```

Use `descriptor.getModelName()` in the request and `descriptor.getEndpoint()` in `RestClient.uri`. Delete `endpoint()` and `modelName()`, remove the `ModelProviderKind` import, and preserve all parsing/timeout/failure logic unchanged.

- [ ] **Step 4: Implement the narrow Factory**

```java
public final class ModelProviderAdapterFactory {
    public ModelExpressionPort create(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            ModelProviderDescriptor descriptor,
            String selectedApiKey,
            int maxTokens
    ) {
        return new OpenAiCompatibleModelExpressionAdapter(
                builder,
                objectMapper,
                new ModelPromptFactory(objectMapper),
                Objects.requireNonNull(descriptor, "descriptor"),
                selectedApiKey == null ? "" : selectedApiKey.strip(),
                maxTokens);
    }
}
```

The Factory must not accept both Provider keys or `ModelExpressionProperties`. A blank
selected key is allowed so the default-disabled application can start; `ModelPolicy`
must remain disabled and `ModelAnswerCoordinator` must not invoke the adapter in that
state.

- [ ] **Step 5: Make properties and configuration Registry-aware**

```java
public String apiKeyFor(ModelProviderKind provider) {
    Objects.requireNonNull(provider, "provider");
    Map<ModelProviderKind, String> configured = new EnumMap<>(ModelProviderKind.class);
    configured.put(ModelProviderKind.DEEPSEEK_V4_FLASH, normalize(deepseekApiKey));
    configured.put(ModelProviderKind.GLM_4_7, normalize(glmApiKey));
    return configured.get(provider);
}
```

Add:

```java
@Bean
ModelProviderRegistrySnapshot modelProviderRegistry() {
    return ModelProviderRegistrySnapshot.builtIn();
}
```

When building `ModelPolicy`, set `selectedApiKeyPresent` only if `apiKeyFor(selected)` is nonblank and Registry compatibility is true. When building the port, resolve exactly the selected Descriptor and pass only its selected key to the Factory. Preserve the existing timeout-bound `HttpClient`.

- [ ] **Step 6: Run model tests and static branch check**

Run all tests under `answer/adapter/model`. Then run:

```powershell
rg -n "ModelProviderKind|DEEPSEEK_V4_FLASH|GLM_4_7" backend/src/main/java/com/portfolio/agent/answer/adapter/model/OpenAiCompatibleModelExpressionAdapter.java
```

Expected: tests pass and the static search returns no matches. Run `git diff --check`; do not stage.

---

### Task 3: Fix Registry Version in AgentExecutionSnapshot

**Files:**

- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/AgentExecutionSnapshot.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/AgentExecutionSnapshotFactory.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/service/AgentExecutionSnapshotFactoryTest.java`
- Modify direct constructors in `PortfolioAgentRuntimeModelPrivacyTest.java` and `PortfolioAgentRuntimeToolTest.java`.

**Interfaces:**

- `AgentExecutionSnapshot.getRegistrySnapshotVersion()` returns a nonblank immutable version.
- `AgentExecutionSnapshotFactory` depends on gateway `ModelProviderRegistry`, never on adapter classes.

- [ ] **Step 1: Write the failing snapshot test**

```java
private static final class TestModelProviderRegistry
        implements ModelProviderRegistry {
    @Override
    public String getSnapshotVersion() { return "c3-model-registry-v1"; }

    @Override
    public boolean supports(
            ModelProviderKind provider, String policy, String schema) {
        return true;
    }
}
```

Pass this to `AgentExecutionSnapshotFactory` and assert:

```java
assertThat(snapshot.getRegistrySnapshotVersion())
        .isEqualTo("c3-model-registry-v1");
```

Extend forbidden snapshot fields with `apiKey`, `endpoint`, `descriptor`, and `providerResponse`.

- [ ] **Step 2: Run affected service tests and confirm RED**

Expected: constructor/getter mismatch.

- [ ] **Step 3: Add the version field and gateway dependency**

```java
private final String registrySnapshotVersion;

public String getRegistrySnapshotVersion() {
    return registrySnapshotVersion;
}
```

The full constructor accepts this value immediately before `ExecutionBudgets` and rejects blank values. Compatibility overloads pass `"none"`.

`AgentExecutionSnapshotFactory` constructor adds `ModelProviderRegistry modelProviderRegistry` and passes `modelProviderRegistry.getSnapshotVersion()` into every new execution snapshot. Update the two direct test constructions with `"c3-model-registry-v1"`.

- [ ] **Step 4: Run service and architecture tests and confirm GREEN**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend\pom.xml -Dtest=AgentExecutionSnapshotFactoryTest,PortfolioAgentRuntimeModelPrivacyTest,PortfolioAgentRuntimeToolTest,PortfolioAgentRuntimeTest test
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\architecture-check.ps1 -Path backend\src
```

Expected: tests pass; `answer.service` has no adapter import. Run `git diff --check`; do not stage.

---

### Task 4: Guardrails, Documentation, and Complete Verification

**Files:**

- Modify: `scripts/architecture-check.ps1`, `scripts/architecture-check.test.ps1`
- Modify: `scripts/privacy-check.ps1`, `scripts/privacy-check.test.ps1`
- Modify: `README.md`, `SECURITY.md`, `docs/00-文档状态索引.md`
- Modify: `docs/superpowers/specs/2026-07-21-portfolio-agent-future-intelligence-design.md`

**Interfaces:**

- Checkers prevent service-to-adapter coupling and credential-bearing/mutable Registry implementations.
- Documentation marks only Model Provider Registry as implemented within C3.

- [ ] **Step 1: Add failing checker fixtures**

Architecture unsafe fixture:

```java
package com.portfolio.agent.answer.service;
import com.portfolio.agent.answer.adapter.model.ModelProviderRegistrySnapshot;
```

Registry privacy unsafe fixture:

```java
final class UnsafeModelProviderRegistry {
    private final String apiKey = "credential-literal";
    public void register(ModelProviderDescriptor descriptor) { }
}
```

Scope Registry-specific patterns so the legitimate credential holder `ModelExpressionProperties` remains allowed.

- [ ] **Step 2: Run checker tests and confirm RED**

Expected: new fixtures demonstrate missing enforcement.

- [ ] **Step 3: Implement minimal checker rules and confirm GREEN**

Block `answer.service` imports of `answer.adapter.model`. In Descriptor/Registry source files, reject credential fields, mutable `register/remove/replace` APIs, dynamic classpath/file/network discovery, and raw Provider request/response logging. Run both checker test scripts and production scans.

- [ ] **Step 4: Update documentation without overstating C3**

Record `registrySnapshotVersion=c3-model-registry-v1`, two built-in Providers, unchanged environment names, single explicit Provider, and no auto-failover/dynamic discovery. Keep Tool Registry, Hook, Orchestrator, multi-Agent, DurableTask, and persistent sessions marked not admitted and unimplemented.

- [ ] **Step 5: Run complete offline release verification**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend\pom.xml test
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\architecture-check.ps1 -Path backend\src
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\privacy-check.ps1 -Path .
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-release.ps1 -SkipInstall -SkipDockerCheck
git diff --check
git diff --cached --name-only
```

Expected: zero backend failures; frontend check/lint/Vitest/build pass; local retrieval gates pass; architecture/privacy/governance/package scans pass; packaged-JAR Playwright has only documented conditional skips; output ends with `Release verification passed.`; staged list is empty.

- [ ] **Step 6: Run controlled real Provider smoke tests**

Start the packaged JAR separately for DeepSeek V4 Flash with the default timeout and GLM-4.7 with an evidence-based explicit timeout. Send only `questionPresetId=sql-audit-overview` plus approved public context. Output only provider kind, elapsed time, resolution, answerSource, generationMode, verification, section count, and citation count. Never print keys, Prompt, AnswerPlan, Authorization, or response content.

Expected: both can return `ANSWERED / PRESET / MODEL / VERIFIED`; an external outage or timeout produces valid whole-answer `FALLBACK` without cross-Provider traffic.

- [ ] **Step 7: Final integrity report**

Report changed files, test counts, conditional skips, real Provider results, remaining non-admitted C3 capabilities, `git status --short`, and empty staged state. Do not stage, commit, push, or create a PR.
