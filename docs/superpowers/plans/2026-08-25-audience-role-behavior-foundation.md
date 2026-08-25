# Agent 四角色行为基础 Implementation Plan
<!-- DOCUMENT_STATUS: ACTIVE -->

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不引入统一策略层、动态角色或额外模型改写的前提下，完成四角色的 Goal 去角色化、Task 级回答适配、角色新会话状态语义、推荐问题过滤与首页 pending 冻结，为 Frontend Agent 的具体角色入口设计提供稳定接口。

**Architecture:** AudienceRole 继续由前端逐轮显式发送，但不再进入 Goal Provider；Goal 通过后由 `SemanticPlanCompiler` 注入每个 Task。Portfolio 只在 `PortfolioInvocationFactory` 中调整已有 Facet 的顺序，General 只在现有 prompt 中调整表达侧重。前端用“选择不同角色 = 创建全新本地会话”的原子操作隔离消息、草稿、模型、Conversation 与 pending；本计划只实现可测试的状态语义，不冻结角色控件的视觉与布局。

**Tech Stack:** Java 21、Spring Boot 3.5、JUnit 5/AssertJ、Vue 3、TypeScript 5.8、Vitest、PowerShell 文档与架构门禁。

## Global Constraints

- 权威设计：[`../specs/2026-08-25-audience-role-session-switching-design.md`](../specs/2026-08-25-audience-role-session-switching-design.md)。
- 四角色保持闭合枚举：`INTERVIEWER | MENTOR | HR | GUEST`；不支持运行时新增。
- 切换为不同角色必须新建 `AgentSession`；相同角色选择是 no-op；角色不可在原会话内被修改。
- 新角色会话只继承当前公开 Project/Case 上下文，不继承消息、草稿、模型选择、Conversation、ResumeToken、Discussion、pending、失败、通知或 seed。
- 角色不能改变 GoalKind、subjects、facets 集合、dimensions、constraints、requestedSize、depth、授权主体或证据范围。
- 不新增 `AudiencePolicy`、Synthesis/PresentationComposer 角色分支、Claim 级 audience 排序、角色专用 Validator、重试或 feature flag。
- 不实现自然语言角色切换意图分类器；自然语言只能作为本轮内容要求，不能改写会话角色。
- 当前单槽 ResumeToken 方案保持不变：token 按 session 存页面内存，仅活跃 session 镜像到 `sessionStorage`。
- Frontend Agent 负责角色入口、标签位置、图标、颜色、文案与移动端交互；在其设计获批前，不提交这些视觉/UI 代码。
- 本计划结束时 A2-53 仍为 `IN_PROGRESS`；只有真实 Provider 16 样本矩阵和桌面/移动 Browser Exit Gate 取证后才能关闭。

---

## File Map

### Backend

- `backend/src/main/java/com/portfolio/agent/turn/planning/GoalInterpretationInput.java`：删除 Goal 输入上的 `audienceProfile`。
- `backend/src/main/java/com/portfolio/agent/turn/planning/GoalInterpretationInputFactory.java`：停止从 `SurfaceContext` 映射角色到 Goal。
- `backend/src/main/java/com/portfolio/agent/turn/infrastructure/model/GoalInterpretationAdapter.java`：停止投影 `audienceProfile`。
- `backend/src/main/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleService.java`：更新直接构造 Goal 输入的调用点；保留 Plan 编译时的角色注入。
- `backend/src/main/resources/prompts/goal-interpretation-system.txt`：删除 Goal 受众字段与 emphasis 指令。
- `backend/src/main/java/com/portfolio/agent/turn/capability/portfolio/PortfolioInvocationFactory.java`：四角色 Facet 顺序的唯一生产权威。
- `backend/src/main/resources/prompts/general-knowledge-system.txt`：General 的闭合 Audience 表达规则。

### Frontend behavior foundation

- `frontend/src/features/agent/composables/useLocalSessions.ts`：定义有消息或非空草稿的会话保留规则。
- `frontend/src/features/agent/components/AgentWorkspace.vue`：增加角色新会话原子操作、角色化 Preset 过滤；不增加具体角色控件。
- `frontend/src/features/audience/model/audienceTypes.ts`：把提交角色冻结进首页回答快照。
- `frontend/src/features/audience/components/AudienceDialogue.vue`：pending 时真正禁止改角色，并从回答快照渲染角色。

### Tests and governance

- `backend/src/test/java/com/portfolio/agent/turn/planning/GoalInterpretationInputFactoryTest.java`
- `backend/src/test/java/com/portfolio/agent/turn/infrastructure/model/GoalInterpretationAdapterTest.java`
- `backend/src/test/java/com/portfolio/agent/turn/planning/SemanticPlanCompilerTest.java`
- `backend/src/test/java/com/portfolio/agent/turn/capability/portfolio/PortfolioInvocationFactoryTest.java`
- `backend/src/test/java/com/portfolio/agent/turn/capability/portfolio/semantic/PortfolioSemanticResultFactoryTest.java`
- `backend/src/test/java/com/portfolio/agent/turn/infrastructure/model/OpenAiCompatibleGeneralKnowledgeAdapterTest.java`
- `frontend/src/features/agent/composables/useLocalSessions.test.ts`
- `frontend/src/features/agent/components/AgentWorkspace.test.ts`
- `frontend/src/features/audience/components/AudienceDialogue.test.ts`
- `docs/08-当前实现状态.md`、`docs/11-项目演进日志.md`：实施后只记录已证明的行为与证据。

---

### Task 1: Goal Provider 物理去角色化，Task 保留角色

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/turn/planning/GoalInterpretationInput.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/planning/GoalInterpretationInputFactory.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/infrastructure/model/GoalInterpretationAdapter.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleService.java`
- Modify: `backend/src/main/resources/prompts/goal-interpretation-system.txt`
- Modify tests listed in File Map that construct or serialize `GoalInterpretationInput`

**Interfaces:**
- Consumes: `AgentTurnCommand.Ask.surfaceContext.audienceRole` and existing `SemanticPlanCompiler.compile(..., AudienceProfile)`.
- Produces: `GoalInterpretationInput` without a role field; unchanged `SemanticTaskParameters.getAudienceProfile()` on every compiled task.

- [ ] **Step 1: Write failing input/adapter tests**

Add assertions that the same command under all four roles produces the same Goal input and serialized provider JSON:

```java
List<GoalInterpretationInput> inputs = Arrays.stream(AgentTurnCommand.AudienceRole.values())
        .map(role -> factory.create(askWithRole(role), context))
        .toList();
assertThat(inputs).extracting(this::goalInputSignature).containsOnly(goalInputSignature(inputs.getFirst()));
assertThat(capturedRequestJson).doesNotContain("audienceProfile");
```

The signature must include `userText`, recent messages, public subjects, allowed kinds/routes/constraints, mode, discussion state, locked/default subject, route candidates and recent semantic state. It must not accept a role argument.

- [ ] **Step 2: Run the focused tests and confirm RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml '-Dtest=GoalInterpretationInputFactoryTest,GoalInterpretationAdapterTest' test
```

Expected: FAIL because `audienceProfile` is still present in the input/provider projection.

- [ ] **Step 3: Remove the old Goal audience path in one replacement slice**

Make the terminal constructor end with:

```java
public GoalInterpretationInput(
        String userText,
        List<String> recentMessages,
        List<PublicSubjectDescriptor> publicSubjects,
        Set<GoalKind> allowedGoalKinds,
        InterpretationMode interpretationMode,
        DiscussionState discussionState,
        PublicSubjectDescriptor lockedSubject,
        List<RouteCandidate> routeCandidates,
        Set<SemanticRouteProposal.Route> allowedRoutes,
        Set<String> allowedRecommendationConstraints,
        PublicSubjectDescriptor defaultSubject,
        ConversationSemanticState recentSemanticState) {
    // existing validation and assignments, with no audienceProfile field
}
```

Then:

- delete the `audienceProfile` field/getter and every constructor parameter/default;
- remove the role mapping from `GoalInterpretationInputFactory`;
- remove `projection.put("audienceProfile", ...)` from `GoalInterpretationAdapter`;
- remove only the Goal-input role arguments from `AgentTurnLifecycleService` direct constructors;
- delete `audienceProfile` from prompt trusted-field lists and delete “use it to choose suitable emphasis”;
- keep every `planCompiler.compile(..., audience(command))` call unchanged.

- [ ] **Step 4: Add the Plan boundary invariant test**

Compile one fixed `UserGoalProposal` four times and compare:

```java
for (AudienceProfile role : AudienceProfile.values()) {
    SemanticTurnPlan plan = compiler.compile(proposal, RELEASE_ID, context, role)
            .getPlan().orElseThrow().getPlan();
    assertThat(plan.getUserGoals()).isEqualTo(baseline.getUserGoals());
    assertThat(plan.getDependencies()).isEqualTo(baseline.getDependencies());
    assertThat(plan.getTasks()).allSatisfy(task ->
            assertThat(task.getParameters().getAudienceProfile()).isEqualTo(role));
    assertThat(plan.getTasks()).extracting(this::taskSignatureWithoutAudience)
            .containsExactlyElementsOf(baselineTaskSignature);
}
```

`taskSignatureWithoutAudience` must compare task id/type, source GoalKind, parameters, subjects, requested outputs and dependency topology.

- [ ] **Step 5: Run focused and full planning tests**

```powershell
mvn.cmd -f backend/pom.xml '-Dtest=GoalInterpretationInputFactoryTest,GoalInterpretationAdapterTest,SemanticPlanCompilerTest,SemanticRouteValidatorTest,GoalProposalCodecTest' test
```

Expected: PASS; adapter capture has no `audienceProfile`; compiled tasks still carry the selected role.

- [ ] **Step 6: Commit the replacement slice**

```powershell
git add backend/src/main/java/com/portfolio/agent/turn/planning/GoalInterpretationInput.java backend/src/main/java/com/portfolio/agent/turn/planning/GoalInterpretationInputFactory.java backend/src/main/java/com/portfolio/agent/turn/infrastructure/model/GoalInterpretationAdapter.java backend/src/main/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleService.java backend/src/main/resources/prompts/goal-interpretation-system.txt backend/src/test/java/com/portfolio/agent/turn/planning backend/src/test/java/com/portfolio/agent/turn/infrastructure/model/GoalInterpretationAdapterTest.java
git commit -m "refactor(agent): 将角色适配移出目标解释"
```

---

### Task 2: Portfolio 四角色 Facet 顺序与承重事实不变量

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/turn/capability/portfolio/PortfolioInvocationFactory.java`
- Modify: `backend/src/test/java/com/portfolio/agent/turn/capability/portfolio/PortfolioInvocationFactoryTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/turn/capability/portfolio/semantic/PortfolioSemanticResultFactoryTest.java`

**Interfaces:**
- Consumes: `SemanticTaskParameters.AudienceProfile` and the Facet set already selected by Goal/depth.
- Produces: deterministic ordering only; no changed subject scope, Facet set, depth, backend, strategy or evidence authorization.

- [ ] **Step 1: Replace the two-role test with an exact four-role matrix**

Assert these exact orders after filtering to the Facets present in the invocation:

```java
Map<AudienceProfile, List<PortfolioEvidenceInvocation.FacetProfile>> expected = Map.of(
    INTERVIEWER, List.of(IMPLEMENTATION, TECHNICAL_DECISION, VERIFICATION, OUTCOME,
            RESPONSIBILITY, BACKGROUND, LIMITATION),
    MENTOR, List.of(TECHNICAL_DECISION, LIMITATION, IMPLEMENTATION, VERIFICATION,
            OUTCOME, RESPONSIBILITY, BACKGROUND),
    HR, List.of(RESPONSIBILITY, OUTCOME, BACKGROUND, IMPLEMENTATION, VERIFICATION,
            TECHNICAL_DECISION, LIMITATION),
    GUEST, List.of(BACKGROUND, IMPLEMENTATION, OUTCOME, RESPONSIBILITY, VERIFICATION,
            TECHNICAL_DECISION, LIMITATION));
```

For every result also assert `Set.copyOf(actualFacets)`, scope, depth, corpus backend and search strategy equal the baseline.

- [ ] **Step 2: Run the matrix test and confirm RED**

```powershell
mvn.cmd -f backend/pom.xml '-Dtest=PortfolioInvocationFactoryTest' test
```

Expected: FAIL for GUEST because production currently preserves source order.

- [ ] **Step 3: Implement the minimal GUEST order and update the stale comment**

Keep `PortfolioInvocationFactory.prioritize()` as the only policy location. Add the explicit GUEST order; do not read `Claim.audiencePriorities` and do not add another class.

- [ ] **Step 4: Make “关键 Claim 一致” operational in a typed test helper**

In `PortfolioSemanticResultFactoryTest`, define the test-only identity:

```java
private record KeyClaimIdentity(
        String subjectId,
        String claimId,
        AnswerClaimCategory category,
        AnswerAchievementStatus achievementStatus,
        AnswerContributionType contributionType,
        AnswerVerificationBasis verificationBasis,
        AnswerClaimVerificationStatus verificationStatus,
        Set<String> directEvidenceIds) {}
```

Extract only units whose `claim.materiality == AnswerMateriality.KEY`. Across all four roles assert identical:

- `AuthorizedSubjectScope`;
- `PortfolioSemanticResult.Coverage` and omissions;
- Fact depth, Comparison dimensions, or Recommendation requested/selected subjects as applicable;
- the set of `KeyClaimIdentity`.

Do not compare unit order, SUPPORTING units or source-key order; those may differ within the same authorized pool.

- [ ] **Step 5: Run Portfolio tests**

```powershell
mvn.cmd -f backend/pom.xml '-Dtest=PortfolioInvocationFactoryTest,PortfolioSemanticResultFactoryTest' test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/turn/capability/portfolio/PortfolioInvocationFactory.java backend/src/test/java/com/portfolio/agent/turn/capability/portfolio/PortfolioInvocationFactoryTest.java backend/src/test/java/com/portfolio/agent/turn/capability/portfolio/semantic/PortfolioSemanticResultFactoryTest.java
git commit -m "feat(agent): 闭合四角色作品集侧重顺序"
```

---

### Task 3: General Prompt 的轻量四角色适配

**Files:**
- Modify: `backend/src/main/resources/prompts/general-knowledge-system.txt`
- Modify: `backend/src/test/java/com/portfolio/agent/turn/infrastructure/model/OpenAiCompatibleGeneralKnowledgeAdapterTest.java`

**Interfaces:**
- Consumes: existing `GeneralKnowledgeRequest.Audience` JSON field.
- Produces: role-specific emphasis/wording; unchanged schema, depth, kind, subjects, comparison dimensions, caveat and safety.

- [ ] **Step 1: Add a prompt capture test for the complete closed mapping**

Assert the injected system prompt contains all five invariants:

```text
Audience changes emphasis and wording only. It must never change requested kind,
depth, subjects, dimensions, factual caution or safety boundaries.
INTERVIEWER: emphasize mechanism, trade-offs and boundaries.
MENTOR: emphasize learning path, common misconceptions and practice boundaries.
HR: emphasize purpose, impact and limitations; avoid unnecessary low-level detail.
GUEST: use plain language and briefly explain necessary technical terms on first use.
Explicit visitor requirements always override audience preferences.
```

Also retain the existing assertion that request JSON sends the typed audience enum.

- [ ] **Step 2: Run and confirm RED**

```powershell
mvn.cmd -f backend/pom.xml '-Dtest=OpenAiCompatibleGeneralKnowledgeAdapterTest' test
```

Expected: FAIL because the current prompt has no audience semantics.

- [ ] **Step 3: Add exactly the compact prompt block**

Insert the block near the existing trusted request-field instructions. Do not add a runtime Validator, keyword scoring, retry, second Provider call or `AudiencePolicy`.

- [ ] **Step 4: Run the General regression suite**

```powershell
mvn.cmd -f backend/pom.xml '-Dtest=GeneralKnowledgeRequestTest,GeneralKnowledgeGeneratorTest,OpenAiCompatibleGeneralKnowledgeAdapterTest' test
```

Expected: PASS; existing schema/depth/caveat tests remain unchanged.

- [ ] **Step 5: Verify no new downstream role branch exists**

```powershell
rg -n "AudienceProfile|audienceProfile|getAudienceProfile" backend/src/main/java/com/portfolio/agent/turn/capability/synthesis backend/src/main/java/com/portfolio/agent/turn/presentation
```

Expected: no new Synthesis or PresentationComposer role-consumption match. Existing generic task plumbing is acceptable; any behavioral branch is not.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/resources/prompts/general-knowledge-system.txt backend/src/test/java/com/portfolio/agent/turn/infrastructure/model/OpenAiCompatibleGeneralKnowledgeAdapterTest.java
git commit -m "feat(agent): 增加通用回答四角色表达规则"
```

---

### Task 4: 前端角色新会话原子操作与推荐问题过滤

**Files:**
- Modify: `frontend/src/features/agent/composables/useLocalSessions.ts`
- Modify: `frontend/src/features/agent/composables/useLocalSessions.test.ts`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.test.ts`

**Interfaces:**
- Consumes: `AudienceRole`, current `activeCase/activeProject`, existing `sessions.createSession(seed)` and per-session token storage.
- Produces: `sessions.switchAudienceRole(targetRole: AudienceRole, projectSlug: string | null): AgentSession | null` and the Workspace wrapper `switchAudienceRole(targetRole: AudienceRole): boolean`.

- [ ] **Step 1: Add failing session-retention tests**

Test three old-session shapes when `createSession()` runs:

```ts
expect(retained({ messages: [userMessage], draft: '' })).toBe(true)
expect(retained({ messages: [], draft: '未发送草稿' })).toBe(true)
expect(retained({ messages: [], draft: '   ' })).toBe(false)
```

Exercise the public composable rather than exporting the predicate. Verify a draft-only old session remains in `historySessions` and can be selected again.

- [ ] **Step 2: Run and confirm RED**

```powershell
npm.cmd --prefix frontend test -- useLocalSessions.test.ts --run
```

Expected: FAIL because current retention only checks USER messages.

- [ ] **Step 3: Centralize the meaningful-session predicate**

Add inside `useLocalSessions.ts`:

```ts
function isMeaningfulSession(session: AgentSession): boolean {
  return session.messages.some((message) => message.role === 'USER')
    || (session.draft?.trim().length ?? 0) > 0
}
```

Use it in both `historySessions` and the `createSession()` cleanup filter. Pending and failures already have a USER message before the request, so do not add another state channel.

- [ ] **Step 4: Add failing Workspace state tests before implementation**

Cover:

1. different role creates a new active session with only the current Case/Project context;
2. new session has target role and no messages, draft, model selection, Conversation, ResumeToken, Discussion, failure, notice or pending;
3. old session retains its draft/pending/error and role;
4. selecting the same role returns false and changes nothing;
5. fallback Presets require both `AGENT` placement and current session role, preserve snapshot order, cap at 3 and never cross-fill;
6. Case `suggestedQuestions` still wins and stays role-neutral;
7. direct Agent entry without handoff still creates INTERVIEWER, while an existing Home handoff seed creates a separate session with its frozen role.

Test session creation through the composable method. Test Preset behavior by mounting Workspace with different `initialRole` values. Do not expose a test-only DOM control and do not create final visual markup in this task.

- [ ] **Step 5: Implement the atomic role-switch method**

First add this operation to the object returned by `useLocalSessions()`:

```ts
function switchAudienceRole(
  targetRole: AudienceRole,
  projectSlug: string | null,
): AgentSession | null {
  const current = activeSession.value
  if (current === null || current.role === targetRole) return null
  return createSession({ role: targetRole, projectSlug })
}
```

Then add this wrapper in `AgentWorkspace.vue`:

```ts
function switchAudienceRole(targetRole: AudienceRole): boolean {
  const current = sessions.activeSession.value
  if (current === null) return false
  const projectSlug = activeCase.value?.projectSlug
    ?? current.projectSlug
    ?? (props.initialProject || null)
  const created = sessions.switchAudienceRole(targetRole, projectSlug)
  if (created === null) return false
  resume.clearActiveToken()
  return sessions.activeSessionId.value === created.id
}
```

Do not cancel old pending turns and do not mutate `current.role`. If `createSession` can throw, compute inputs first and only clear the token after successful creation.

Update fallback filtering to:

```ts
.filter((preset) => preset.placements.includes('AGENT')
  && preset.audiences.includes(activeSession.value?.role ?? props.initialRole))
.slice(0, 3)
```

- [ ] **Step 6: Add the late-response ResumeToken regression**

Use two deferred Turn promises:

- session A submits and stays pending;
- `switchAudienceRole('HR')` activates new session B with an empty token slot;
- A resolves with `{ conversationId: 'conversation-a', resumeToken: 'token-a' }`;
- assert A owns `token-a`, B owns no token and `sessionStorage[portfolio.agent.resume-token.v1]` remains empty;
- select A and assert the existing watcher mirrors `token-a` to the single slot.

This proves the current memory-per-session/single-active-mirror model; do not create a second storage key.

- [ ] **Step 7: Run frontend behavior tests**

```powershell
npm.cmd --prefix frontend test -- useLocalSessions.test.ts AgentWorkspace.test.ts --run
npm.cmd --prefix frontend run check
```

Expected: PASS.

- [ ] **Step 8: Commit the nonvisual frontend seam**

```powershell
git add frontend/src/features/agent/composables/useLocalSessions.ts frontend/src/features/agent/composables/useLocalSessions.test.ts frontend/src/features/agent/components/AgentWorkspace.vue frontend/src/features/agent/components/AgentWorkspace.test.ts
git commit -m "feat(frontend): 建立角色新会话状态语义"
```

---

### Task 5: 首页 pending 角色冻结

**Files:**
- Modify: `frontend/src/features/audience/model/audienceTypes.ts`
- Modify: `frontend/src/features/audience/components/AudienceDialogue.vue`
- Modify: `frontend/src/features/audience/components/AudienceDialogue.test.ts`

**Interfaces:**
- Consumes: submission-time `SurfaceContext.audienceRole`.
- Produces: `HomeAnswerState.role: AudienceRole`, used by answer rendering and Agent handoff.

- [ ] **Step 1: Write failing pending/snapshot tests**

With a deferred `submitAgentTurn` promise:

- choose MENTOR and submit;
- assert all role buttons have a real `disabled` attribute;
- try clicking HR and assert selected role remains MENTOR;
- resolve the request;
- assert `LightAnswerPanel.role === 'MENTOR'` and the handoff replay surface also remains MENTOR.

After clicking the existing “进入 Agent” action, also assert the emitted/stored `AgentRouteSeed.role` is MENTOR; do not read `selectedRole` during handoff construction.

- [ ] **Step 2: Run and confirm RED**

```powershell
npm.cmd --prefix frontend test -- AudienceDialogue.test.ts --run
```

Expected: FAIL because role buttons are currently operable and rendering reads reactive `selectedRole`.

- [ ] **Step 3: Freeze the role into `HomeAnswerState`**

Add:

```ts
export interface HomeAnswerState {
  role: AudienceRole
  // existing fields unchanged
}
```

When constructing the answer use the request snapshot:

```ts
answer.value = {
  role: surfaceContext.audienceRole,
  // existing fields
}
```

Make `chooseRole` return immediately while `pending`, bind `:disabled="pending"` on every role button, and render `<LightAnswerPanel :role="answer.role" ... />`.

- [ ] **Step 4: Run tests and typecheck**

```powershell
npm.cmd --prefix frontend test -- AudienceDialogue.test.ts --run
npm.cmd --prefix frontend run check
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add frontend/src/features/audience/model/audienceTypes.ts frontend/src/features/audience/components/AudienceDialogue.vue frontend/src/features/audience/components/AudienceDialogue.test.ts
git commit -m "fix(frontend): 冻结首页提交角色快照"
```

---

### Task 6: Deterministic gate、内容基线与前端设计交接

**Files:**
- Modify after evidence: `docs/08-当前实现状态.md`
- Modify after evidence: `docs/11-项目演进日志.md`
- Do not modify yet: `docs/15-Agent 2.0真实交互问题清单与修复边界.md`
- Create by Frontend Agent after review starts: `docs/superpowers/specs/2026-08-25-audience-role-switching-frontend-ui-design.md`

**Interfaces:**
- Consumes: Tasks 1–5 passing commits.
- Produces: deterministic behavior foundation and an exact UI-design handoff; it does not close A2-53.

- [ ] **Step 1: Re-run the public Preset coverage audit without making it a permanent cardinality contract**

Read `backend/src/main/resources/public-data/bundle/portfolio.json` and report, for each role, the count and ids where `placements` contains `AGENT` and `audiences` contains the role.

Expected current snapshot: 14 matching presets for each of INTERVIEWER, MENTOR, HR and GUEST. If the snapshot changed, record the actual count; do not fail merely because it is below 3. Unit tests must use synthetic fixtures to prove “at most 3/no cross-fill”.

- [ ] **Step 2: Run deterministic gates**

```powershell
mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run check
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/agent-architecture-status.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/agent-architecture-status.ps1
```

Expected: all deterministic/test commands PASS; architecture status may remain `IN_PROGRESS` because Provider/Browser evidence has not run.

- [ ] **Step 3: Update current-state and evolution docs with evidence-bounded wording**

In `docs/08-当前实现状态.md`, state only:

- Goal provider no longer sees role;
- every Task still receives the closed role;
- Portfolio has the fixed four-role ordering;
- General prompt has the closed mapping;
- frontend state seam creates a new session for a different role;
- concrete role control/labels and real Provider/Browser matrix remain pending.

Add one dated event in `docs/11-项目演进日志.md` linking this plan and its commits. Do not mark A2-53 complete and do not write raw prompt/answer text into status files.

- [ ] **Step 4: Hand off the concrete UI design to Frontend Agent**

The Frontend Agent must use the approved parent design and produce the named UI design spec covering only:

- role entry point in current-session area;
- role identity in each history row;
- same-role disabled/hidden behavior;
- different-role confirmation or immediate action;
- draft/pending affordance;
- desktop/mobile layouts and keyboard/accessibility behavior;
- how the UI calls `switchAudienceRole(targetRole)` without mutating role in place.

It must not change backend semantics, add an NL intent classifier, introduce role colors as data contracts, or reuse another-role session. Obtain user approval of that UI spec before writing its separate implementation plan.

- [ ] **Step 5: Commit evidence-bounded documentation**

```powershell
git add docs/08-当前实现状态.md docs/11-项目演进日志.md
git commit -m "docs(agent): 记录四角色行为基础证据"
```

---

## Deferred Exit Gate (not part of this foundation plan)

The following is deliberately not executable until the Frontend Agent UI design is approved and implemented:

1. desktop and mobile Browser evidence for role visibility, new-session switching, pending isolation, same-role no-op and Home handoff;
2. the fixed 16-sample real Provider matrix (4 question classes × 4 roles), with typed assertions and no raw answer text in governance files;
3. A2-53 closure and `docs/agent-architecture-status.json` update based on fresh evidence.

If real Provider credentials or a Browser environment are unavailable, record that layer as `WAIVED`/`NOT_RUN` with reason and keep A2-53 `IN_PROGRESS`; never promote deterministic tests into Provider or Browser PASS.
