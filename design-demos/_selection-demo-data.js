/* ════════════════════════════════════════════════════════════════════
   资产包组合推荐 · 原型 mock 数据（_selection-demo-data.js）

   严格对齐后端 DTO（backend/.../selection/dto/）：
     POST /api/portfolio-selections
     PortfolioSelectionResponse 字段名与句式逐字复刻：
       · selectionReason  = 「覆盖能力 A、B，并提供已批准公开证据」（后端机械生成）
       · complementarity.reason = 「共同覆盖互补能力：A、B」（能力码并集 sorted）
       · alternatives[].reason  = 「候选有效，但当前组合的能力覆盖与互补性更高」
       · coverage[].label = capabilityCode 本身（后端不给中文，前端映射）
       · degradation.message = 后端固定五句文案

   事实来源：backend/src/main/resources/public-data/bundle/portfolio.json
     （contentVersion 2026-07-29.1，仅 APPROVED 公开内容）

   隐私红线：subjectId / claimId / evidenceId 只做内存映射，绝不渲染。
   ════════════════════════════════════════════════════════════════════ */
window.SELECTION_DEMO = {

  /* ── 求职方向（request.careerTrack，自由串；前端收为受控三选） ── */
  careerTracks: [
    { code: '',            label: '不限定' },
    { code: 'JAVA_BACKEND', label: 'Java 后端' },
    { code: 'AGENT',       label: 'Agent 工程' },
  ],

  /* ── 访客身份（request.audienceRole，必填，后端六值枚举） ── */
  roles: [
    { code: 'INTERVIEWER',     label: '面试官',      desc: '关注整体交付与成长主线' },
    { code: 'TECH_INTERVIEWER', label: '技术面试官',  desc: '关注技术深度与验证过程' },
    { code: 'MENTOR',          label: '未来导师',    desc: '关注学习能力与协作潜力' },
    { code: 'HR',              label: 'HR / 招聘者', desc: '关注岗位匹配与稳定产出' },
    { code: 'GUEST',           label: '普通访客',    desc: '先随便看看公开作品' },
    { code: 'PEER_DEVELOPER',  label: '同行开发者',  desc: '关注实现细节与工程判断' },
  ],

  /* ── 能力码 → 中文（前端展示映射；后端 coverage.label 原样返回 code） ── */
  capabilityLabels: {
    DELIVERY: '交付',
    VERIFICATION: '验证',
    IMPLEMENTATION: '实现',
    TEST_TOOLING: '测试工具',
    EVALUATION: '评测',
    CODE_NAVIGATION: '代码导航',
    INCIDENT: '故障排查',
    FAILURE_ISOLATION: '故障隔离',
    ASYNC_TASK: '异步任务',
    KNOWLEDGE_ASSET: '知识资产',
    DOCUMENTED_OUTPUT: '文档化产出',
    MULTILINGUAL_MEDIA: '多语言媒体',
    INPUT_SAFETY: '输入安全',
    FALLBACK: '降级设计',
  },

  /* ── 表单里可选的能力目标（受控多选，来自当前公开资产实际出现的能力码） ── */
  capabilityOptions: [
    'DELIVERY', 'VERIFICATION', 'IMPLEMENTATION', 'TEST_TOOLING',
    'EVALUATION', 'INCIDENT', 'FAILURE_ISOLATION', 'INPUT_SAFETY',
  ],

  /* ════════════════════════════════════════════════════════════════
     六种响应场景。responses 的 key 供原型控制条切换。
     ════════════════════════════════════════════════════════════════ */
  responses: {

    /* ── 1. READY · HYBRID · 3/3 ─────────────────────────────── */
    ready: {
      selectionId: 'sel_9f2ac41d07b34e0c9a55f01d2c6e8b73d4a1f09c8e2b6d5a3c7f1e09482b6d5a3c7',
      releaseVersion: '2026-07-29.1',
      policyVersion: 'selection-v1',
      retrievalMode: 'HYBRID',
      selectionMode: 'EXHAUSTIVE',
      status: 'READY',
      requestedSize: 3,
      actualSize: 3,
      items: [
        {
          subjectId: 'PROJECT-01',
          subjectType: 'PROJECT',
          title: 'SQL 审计与故障排查工具',
          summary: '把分散在远程服务器上的 SQL 日志检索、结果追溯和归档流程整合为可复用的内部工具。',
          route: '/projects/sql-audit',
          careerTrack: 'JAVA_BACKEND',
          capabilities: ['DELIVERY', 'FAILURE_ISOLATION', 'IMPLEMENTATION', 'INPUT_SAFETY', 'VERIFICATION'],
          selectionReason: '覆盖能力 DELIVERY、FAILURE_ISOLATION、IMPLEMENTATION、INPUT_SAFETY、VERIFICATION，并提供已批准公开证据',
          evidenceRefs: [
            { claimId: 'C-DEMO-0001', evidenceId: 'E-01', label: 'E-01 SQL 审计工具交付证据集' },
            { claimId: 'C-DEMO-0002', evidenceId: 'E-02', label: 'E-02 七月输入安全与多来源迭代证据集' },
          ],
        },
        {
          subjectId: 'CASE-02',
          subjectType: 'CASE',
          title: '测试角色重置工具',
          summary: '为重复验证新账号流程提供受控的角色查询与重置入口。',
          route: '/cases/test-role-reset',
          careerTrack: 'JAVA_BACKEND',
          capabilities: ['DELIVERY', 'TEST_TOOLING', 'VERIFICATION'],
          selectionReason: '覆盖能力 DELIVERY、TEST_TOOLING、VERIFICATION，并提供已批准公开证据',
          evidenceRefs: [
            { claimId: 'C-DEMO-0003', evidenceId: 'E-04', label: 'E-04 测试角色重置说明与验收证据集' },
          ],
        },
        {
          subjectId: 'CASE-03',
          subjectType: 'CASE',
          title: '代码图谱工具端到端评测',
          summary: '通过符号检索样本和两组工具任务评估代码图谱的收益与失效边界。',
          route: '/cases/codegraph-evaluation',
          careerTrack: 'JAVA_BACKEND',
          capabilities: ['CODE_NAVIGATION', 'EVALUATION', 'VERIFICATION'],
          selectionReason: '覆盖能力 CODE_NAVIGATION、EVALUATION、VERIFICATION，并提供已批准公开证据',
          evidenceRefs: [
            { claimId: 'C-DEMO-0004', evidenceId: 'E-05', label: 'E-05 代码图谱端到端评测报告集' },
          ],
        },
      ],
      coverage: [
        { capabilityCode: 'DELIVERY',     label: 'DELIVERY',     coveredBySubjectIds: ['CASE-02', 'PROJECT-01'] },
        { capabilityCode: 'EVALUATION',   label: 'EVALUATION',   coveredBySubjectIds: ['CASE-03'] },
        { capabilityCode: 'TEST_TOOLING', label: 'TEST_TOOLING', coveredBySubjectIds: ['CASE-02'] },
        { capabilityCode: 'VERIFICATION', label: 'VERIFICATION', coveredBySubjectIds: ['CASE-02', 'CASE-03', 'PROJECT-01'] },
      ],
      complementarity: [
        { leftSubjectId: 'PROJECT-01', rightSubjectId: 'CASE-02',
          reason: '共同覆盖互补能力：DELIVERY、FAILURE_ISOLATION、IMPLEMENTATION、INPUT_SAFETY、TEST_TOOLING、VERIFICATION' },
        { leftSubjectId: 'PROJECT-01', rightSubjectId: 'CASE-03',
          reason: '共同覆盖互补能力：CODE_NAVIGATION、DELIVERY、EVALUATION、FAILURE_ISOLATION、IMPLEMENTATION、INPUT_SAFETY、VERIFICATION' },
        { leftSubjectId: 'CASE-02', rightSubjectId: 'CASE-03',
          reason: '共同覆盖互补能力：CODE_NAVIGATION、DELIVERY、EVALUATION、TEST_TOOLING、VERIFICATION' },
      ],
      alternatives: [
        {
          subjectId: 'PROJECT-02', subjectType: 'PROJECT',
          title: '活动系统工程实践',
          summary: '围绕活动复开持续承担后端配置协助、部分功能开发和故障排查，按案例保留个人贡献与验收边界。',
          route: '/projects/activity-engineering',
          reason: '候选有效，但当前组合的能力覆盖与互补性更高',
        },
        {
          subjectId: 'CASE-01', subjectType: 'CASE',
          title: '多语言图片上传结果保留修复',
          summary: '修复后续语言上传覆盖既有语言映射的问题。',
          route: '/cases/multilingual-image-preservation',
          reason: '候选有效，但当前组合的能力覆盖与互补性更高',
        },
        {
          subjectId: 'CASE-09', subjectType: 'CASE',
          title: '重复配置导致唯一性冲突',
          summary: '通过错误日志定位重复数据，清理后刷新版本并验证页面恢复。',
          route: '/cases/a-01-incident',
          reason: '候选有效，但当前组合的能力覆盖与互补性更高',
        },
      ],
      degradation: null,
    },

    /* ── 2. INSUFFICIENT · 2/3 + 未覆盖能力行 ──────────────────
       后端语义：candidates < requestedSize → INSUFFICIENT_ELIGIBLE_ASSETS；
       coverage 独立计算，请求了 ASYNC_TASK 但无资产覆盖 → 空数组行。 */
    insufficient: {
      selectionId: 'sel_1b7d92e4a0f34c5b8d6e2f9a1c3b5d7e9f0a2c4b6d8e0f2a4c6b8d0e2f4a6c8e0',
      releaseVersion: '2026-07-29.1',
      policyVersion: 'selection-v1',
      retrievalMode: 'HYBRID',
      selectionMode: 'EXHAUSTIVE',
      status: 'INSUFFICIENT',
      requestedSize: 3,
      actualSize: 2,
      items: [
        {
          subjectId: 'PROJECT-01',
          subjectType: 'PROJECT',
          title: 'SQL 审计与故障排查工具',
          summary: '把分散在远程服务器上的 SQL 日志检索、结果追溯和归档流程整合为可复用的内部工具。',
          route: '/projects/sql-audit',
          careerTrack: 'JAVA_BACKEND',
          capabilities: ['DELIVERY', 'FAILURE_ISOLATION', 'IMPLEMENTATION', 'INPUT_SAFETY', 'VERIFICATION'],
          selectionReason: '覆盖能力 DELIVERY、FAILURE_ISOLATION、IMPLEMENTATION、INPUT_SAFETY、VERIFICATION，并提供已批准公开证据',
          evidenceRefs: [
            { claimId: 'C-DEMO-0001', evidenceId: 'E-01', label: 'E-01 SQL 审计工具交付证据集' },
          ],
        },
        {
          subjectId: 'PROJECT-03',
          subjectType: 'PROJECT',
          title: '测试角色重置工具',
          summary: '为重复验证新账号流程提供受控的角色查询、确认、重置和复查入口。',
          route: '/projects/role-reset-tool',
          careerTrack: 'JAVA_BACKEND',
          capabilities: ['DELIVERY', 'TEST_TOOLING'],
          selectionReason: '覆盖能力 DELIVERY、TEST_TOOLING，并提供已批准公开证据',
          evidenceRefs: [
            { claimId: 'C-DEMO-0003', evidenceId: 'E-04', label: 'E-04 测试角色重置说明与验收证据集' },
          ],
        },
      ],
      coverage: [
        { capabilityCode: 'ASYNC_TASK',   label: 'ASYNC_TASK',   coveredBySubjectIds: [] },
        { capabilityCode: 'DELIVERY',     label: 'DELIVERY',     coveredBySubjectIds: ['PROJECT-01', 'PROJECT-03'] },
        { capabilityCode: 'TEST_TOOLING', label: 'TEST_TOOLING', coveredBySubjectIds: ['PROJECT-03'] },
        { capabilityCode: 'VERIFICATION', label: 'VERIFICATION', coveredBySubjectIds: ['PROJECT-01'] },
      ],
      complementarity: [
        { leftSubjectId: 'PROJECT-01', rightSubjectId: 'PROJECT-03',
          reason: '共同覆盖互补能力：DELIVERY、FAILURE_ISOLATION、IMPLEMENTATION、INPUT_SAFETY、TEST_TOOLING、VERIFICATION' },
      ],
      alternatives: [
        {
          subjectId: 'PROJECT-02', subjectType: 'PROJECT',
          title: '活动系统工程实践',
          summary: '围绕活动复开持续承担后端配置协助、部分功能开发和故障排查，按案例保留个人贡献与验收边界。',
          route: '/projects/activity-engineering',
          reason: '候选有效，但当前组合的能力覆盖与互补性更高',
        },
      ],
      degradation: { code: 'INSUFFICIENT_ELIGIBLE_ASSETS', message: '符合公开证据门槛的资产少于请求数量' },
    },

    /* ── 3. READY 但 FTS_ONLY 降级（向量召回不可用 → 全文检索） ── */
    ftsOnly: {
      selectionId: 'sel_4e6a8c0e2f4a6b8d0e2f4a6b8d0e2f4a6b8d0e2f4a6b8d0e2f4a6b8d0e2f4a6b8',
      releaseVersion: '2026-07-29.1',
      policyVersion: 'selection-v1',
      retrievalMode: 'FTS_ONLY',
      selectionMode: 'EXHAUSTIVE',
      status: 'READY',
      requestedSize: 3,
      actualSize: 3,
      items: [
        {
          subjectId: 'PROJECT-01',
          subjectType: 'PROJECT',
          title: 'SQL 审计与故障排查工具',
          summary: '把分散在远程服务器上的 SQL 日志检索、结果追溯和归档流程整合为可复用的内部工具。',
          route: '/projects/sql-audit',
          careerTrack: 'JAVA_BACKEND',
          capabilities: ['DELIVERY', 'FAILURE_ISOLATION', 'IMPLEMENTATION', 'INPUT_SAFETY', 'VERIFICATION'],
          selectionReason: '覆盖能力 DELIVERY、FAILURE_ISOLATION、IMPLEMENTATION、INPUT_SAFETY、VERIFICATION，并提供已批准公开证据',
          evidenceRefs: [
            { claimId: 'C-DEMO-0001', evidenceId: 'E-01', label: 'E-01 SQL 审计工具交付证据集' },
            { claimId: 'C-DEMO-0002', evidenceId: 'E-02', label: 'E-02 七月输入安全与多来源迭代证据集' },
          ],
        },
        {
          subjectId: 'CASE-02',
          subjectType: 'CASE',
          title: '测试角色重置工具',
          summary: '为重复验证新账号流程提供受控的角色查询与重置入口。',
          route: '/cases/test-role-reset',
          careerTrack: 'JAVA_BACKEND',
          capabilities: ['DELIVERY', 'TEST_TOOLING', 'VERIFICATION'],
          selectionReason: '覆盖能力 DELIVERY、TEST_TOOLING、VERIFICATION，并提供已批准公开证据',
          evidenceRefs: [
            { claimId: 'C-DEMO-0003', evidenceId: 'E-04', label: 'E-04 测试角色重置说明与验收证据集' },
          ],
        },
        {
          subjectId: 'CASE-03',
          subjectType: 'CASE',
          title: '代码图谱工具端到端评测',
          summary: '通过符号检索样本和两组工具任务评估代码图谱的收益与失效边界。',
          route: '/cases/codegraph-evaluation',
          careerTrack: 'JAVA_BACKEND',
          capabilities: ['CODE_NAVIGATION', 'EVALUATION', 'VERIFICATION'],
          selectionReason: '覆盖能力 CODE_NAVIGATION、EVALUATION、VERIFICATION，并提供已批准公开证据',
          evidenceRefs: [
            { claimId: 'C-DEMO-0004', evidenceId: 'E-05', label: 'E-05 代码图谱端到端评测报告集' },
          ],
        },
      ],
      coverage: [
        { capabilityCode: 'DELIVERY',     label: 'DELIVERY',     coveredBySubjectIds: ['CASE-02', 'PROJECT-01'] },
        { capabilityCode: 'EVALUATION',   label: 'EVALUATION',   coveredBySubjectIds: ['CASE-03'] },
        { capabilityCode: 'TEST_TOOLING', label: 'TEST_TOOLING', coveredBySubjectIds: ['CASE-02'] },
        { capabilityCode: 'VERIFICATION', label: 'VERIFICATION', coveredBySubjectIds: ['CASE-02', 'CASE-03', 'PROJECT-01'] },
      ],
      complementarity: [
        { leftSubjectId: 'PROJECT-01', rightSubjectId: 'CASE-02',
          reason: '共同覆盖互补能力：DELIVERY、FAILURE_ISOLATION、IMPLEMENTATION、INPUT_SAFETY、TEST_TOOLING、VERIFICATION' },
        { leftSubjectId: 'PROJECT-01', rightSubjectId: 'CASE-03',
          reason: '共同覆盖互补能力：CODE_NAVIGATION、DELIVERY、EVALUATION、FAILURE_ISOLATION、IMPLEMENTATION、INPUT_SAFETY、VERIFICATION' },
        { leftSubjectId: 'CASE-02', rightSubjectId: 'CASE-03',
          reason: '共同覆盖互补能力：CODE_NAVIGATION、DELIVERY、EVALUATION、TEST_TOOLING、VERIFICATION' },
      ],
      alternatives: [],
      degradation: { code: 'VECTOR_RETRIEVAL_UNAVAILABLE', message: '当前使用全文检索完成候选召回' },
    },

    /* ── 4. TEMPORARILY_UNAVAILABLE（HTTP 200，四数组全空） ───── */
    unavailable: {
      selectionId: 'sel_0000000000000000000000000000000000000000000000000000000000000000',
      releaseVersion: '2026-07-29.1',
      policyVersion: 'selection-v1',
      retrievalMode: 'UNAVAILABLE',
      selectionMode: 'EXHAUSTIVE',
      status: 'TEMPORARILY_UNAVAILABLE',
      requestedSize: 3,
      actualSize: 0,
      items: [],
      coverage: [],
      complementarity: [],
      alternatives: [],
      degradation: { code: 'PUBLIC_SELECTION_UNAVAILABLE', message: '组合推荐暂时不可用，请使用现有作品浏览入口' },
    },

    /* ── 5. READY 但全空（防御态：后端异常返回，前端 fail-safe） ── */
    empty: {
      selectionId: 'sel_empty_guard0000000000000000000000000000000000000000000000000000',
      releaseVersion: '2026-07-29.1',
      policyVersion: 'selection-v1',
      retrievalMode: 'HYBRID',
      selectionMode: 'EXHAUSTIVE',
      status: 'READY',
      requestedSize: 3,
      actualSize: 0,
      items: [],
      coverage: [],
      complementarity: [],
      alternatives: [],
      degradation: null,
    },
  },
};
