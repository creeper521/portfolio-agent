import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(scriptDirectory, "..");
const portfolioPath = path.join(
  repositoryRoot,
  "backend",
  "src",
  "main",
  "resources",
  "public-data",
  "bundle",
  "portfolio.json",
);

const portfolio = JSON.parse(fs.readFileSync(portfolioPath, "utf8"));

if (portfolio.schemaVersion !== "3.0") {
  throw new Error(`Expected schemaVersion 3.0, got ${portfolio.schemaVersion}`);
}
if (portfolio.contentVersion !== "2026-07-27.1") {
  throw new Error(
    `Expected contentVersion 2026-07-27.1, got ${portfolio.contentVersion}`,
  );
}

const projectsById = new Map(
  portfolio.projects.map((project) => [project.id, project]),
);
const requiredProject = (id) => {
  const project = projectsById.get(id);
  if (!project) {
    throw new Error(`Missing source project: ${id}`);
  }
  return project;
};

const classifyProject = (
  project,
  {
    code,
    careerTrack,
    projectNature,
    displayTier,
    featuredCaseIds,
    overrides = {},
  },
) => ({
  ...project,
  code,
  ...overrides,
  careerTrack,
  projectNature,
  displayTier,
  featuredCaseIds,
});

const sqlProject = classifyProject(requiredProject("sql-audit-project"), {
  code: "P-01",
  careerTrack: "JAVA_BACKEND",
  projectNature: "TOOL",
  displayTier: "PRIMARY",
  featuredCaseIds: [],
});

const activityProject = classifyProject(
  requiredProject("activity-engineering-project"),
  {
    code: "P-02",
    careerTrack: "JAVA_BACKEND",
    projectNature: "WORKSTREAM",
    displayTier: "PRIMARY",
    featuredCaseIds: [
      "case-public-k-10",
      "case-public-a-01",
      "case-public-a-05",
      "case-public-a-06",
      "case-public-a-14",
      "case-public-a-15",
    ],
    overrides: {
      summary:
        "围绕活动复开持续承担后端配置协助、部分功能开发和故障排查，按案例保留个人贡献与验收边界。",
      background:
        "实际工作贯穿多次活动复开：既包含后端配置与数据链路协助，也包含局部功能开发、缓存刷新和线上问题定位。公开内容不包含内部系统标识、环境信息或原始材料。",
      responsibilities: [
        "配合活动复开完成后端配置检查、数据准备、缓存与版本刷新。",
        "承担部分功能开发，并围绕展示、奖励、初始化和资源下发问题进行链路排查。",
        "与测试、客户端及其他后端同事协作，明确服务端结论、待验证项和转交边界。",
      ],
      solution:
        "以活动复开流程为主线，把配置、数据、缓存、服务端下发和客户端表现拆成可复查的排查层级；每次工作按真实成熟度、贡献方式和证据状态沉淀为案例。",
      outcome:
        "形成覆盖功能开发、后端配置协助和多类活动复开故障排查的工作主线；未完成最终复测的事项明确标记为已排查而非已修复。",
    },
  },
);

const roleResetProject = {
  id: "role-reset-tool-project",
  code: "P-03",
  slug: "role-reset-tool",
  title: "测试角色重置工具",
  summary:
    "为重复验证新账号流程提供受控的角色查询、确认、重置和复查入口。",
  background:
    "旧角色状态和缓存会干扰需要反复创建新角色的测试流程，因此需要一个限制目标环境并带明确确认的内部工具。",
  responsibilities: [
    "实现受控环境选择、角色查询和重置流程。",
    "为破坏性操作增加影响提示与二次确认。",
    "完成查询、重置、复查和使用说明的交付闭环。",
  ],
  solution:
    "把环境选择、角色查询、重置确认和结果复查拆成明确步骤，限制可操作范围，并用抽象标识和脱敏验收记录保持公开边界。",
  keyDecisions: [
    "环境选择、查询和破坏性操作分步进行。",
    "重置前必须显示明确确认，避免误操作。",
    "公开材料只保留行为、验收流程和安全边界。",
  ],
  technologies: ["Java", "Spring Boot", "测试工具", "安全确认"],
  verification: [
    "在批准环境中查询到目标角色。",
    "确认重置后旧角色无法继续查询。",
    "重新登录后能够创建新角色并获得新的抽象标识。",
  ],
  outcome:
    "工具已完成并用于重复创建新账号状态，降低旧角色缓存对测试流程的干扰。",
  handoff: "已形成使用说明；内部数据结构、环境与示例标识保持私有。",
  status: "DELIVERED",
  contributionType: "PRIMARY",
  claimIds: ["claim-role-reset-tool-project-delivered"],
  evidenceIds: ["evidence-case-role-reset-guide-and-acceptance"],
  timelineEventIds: ["timeline-case-role-reset-delivery"],
  careerTrack: "JAVA_BACKEND",
  projectNature: "TOOL",
  displayTier: "PRIMARY",
  featuredCaseIds: ["case-role-reset"],
};

const agentProject = classifyProject(
  requiredProject("personal-agent-platform-project"),
  {
    code: "P-04",
    careerTrack: "AGENT",
    projectNature: "INTEGRATION_PROTOTYPE",
    displayTier: "PRIMARY",
    featuredCaseIds: [
      "case-public-t-08",
      "case-public-t-09",
      "case-public-t-10",
      "case-public-t-11",
    ],
    overrides: {
      title: "Agent 能力集成 MVP",
      summary:
        "用于学习和打通 Prompt、Skills、RAG、MCP、Memory 与 Agent 的最小可运行原型，不宣称生产化或持续演进。",
      background:
        "该项目以理解 AI Agent 的关键概念和协作边界为目标，通过最小实现把提示词、技能、检索、工具调用、记忆和 Agent 编排串成可验证链路。",
      responsibilities: [
        "分别实现并验证 Prompt、Skills、RAG、MCP、Memory 与 Agent 的最小能力。",
        "把分散概念连接为可运行 MVP，记录各环节的输入、输出和限制。",
        "维护公开表述边界，不把学习型集成原型描述为生产系统。",
      ],
      solution:
        "采用逐能力最小验证再集成的方式，先确认检索、工具连接、会话记忆和语言偏好等单点链路，再组合为端到端 Agent MVP。",
      technologies: ["Prompt", "Skills", "RAG", "MCP", "Memory", "Agent", "Vue"],
      outcome:
        "完成关键 Agent 概念的最小链路打通与本地验证，形成可用于继续实验的 MVP；生产化、稳定性与完整工程边界仍未覆盖。",
      handoff:
        "当前定位为阶段性学习与集成成果，不表述为持续演进项目或生产交付。",
    },
  },
);

const imageProject = classifyProject(requiredProject("image-audit-project"), {
  code: "P-05",
  careerTrack: "JAVA_BACKEND",
  projectNature: "TOOL",
  displayTier: "SECONDARY",
  featuredCaseIds: ["case-multilingual-upload", "case-public-t-07"],
  overrides: {
    responsibilities: [
      "修复多语言图片分次上传时既有映射被覆盖的问题。",
      "实现并验证图片上传审计原型，保留未确认发布的状态边界。",
    ],
  },
});

const collections = [
  {
    id: "open-source-evaluation",
    slug: "open-source-evaluation",
    title: "开源项目体验测试",
    summary:
      "聚合代码图谱、上下文压缩及辅助开发工具的实际体验与评测案例，记录可用能力、问题和适用边界。",
    displayOrder: 10,
  },
  {
    id: "engineering-operations",
    slug: "engineering-operations",
    title: "工程操作与实践",
    summary:
      "聚合构建产物替换、打包交付、本地持续集成、容器化和工程环境操作案例，不将单次实践包装成完整项目。",
    displayOrder: 20,
  },
  {
    id: "technical-writing",
    slug: "technical-writing",
    title: "技术写作",
    summary:
      "聚合实际工作中的技术长文、内部分享和专题手册；是否公开及原创边界按具体案例说明。",
    displayOrder: 30,
  },
];

const openSourceCaseIds = new Set([
  "case-codegraph-evaluation",
  "case-public-t-12",
  "case-public-t-13",
  "case-public-k-02",
  "case-public-k-04",
  "case-public-k-05",
]);
const engineeringCaseIds = new Set([
  "case-public-t-14",
  "case-public-t-15",
  "case-public-t-19",
  "case-public-k-06",
  "case-public-k-09",
  "case-public-k-11",
  "case-public-k-12",
  "case-public-k-14",
  "case-public-k-15",
]);
const writingCaseIds = new Set([
  "case-public-t-18",
  "case-public-k-16",
  "case-public-k-17",
]);

const investigationCaseIds = new Set(
  portfolio.cases
    .filter((item) => {
      const caseNumber = Number(item.code.replace(/\D/g, ""));
      return caseNumber >= 22 && caseNumber <= 36;
    })
    .map((item) => item.id),
);
if (investigationCaseIds.size !== 15) {
  throw new Error(
    `Expected 15 investigated cases, got ${investigationCaseIds.size}`,
  );
}

const cases = portfolio.cases.map((item) => {
  let projectId = item.projectId;
  let collectionIds = [];

  if (item.id === "case-role-reset") {
    projectId = "role-reset-tool-project";
  } else if (item.id === "case-multilingual-upload") {
    projectId = "image-audit-project";
  } else if (
    openSourceCaseIds.has(item.id) ||
    engineeringCaseIds.has(item.id) ||
    writingCaseIds.has(item.id)
  ) {
    projectId = null;
  }

  if (openSourceCaseIds.has(item.id)) {
    collectionIds = ["open-source-evaluation"];
  } else if (engineeringCaseIds.has(item.id)) {
    collectionIds = ["engineering-operations"];
  } else if (writingCaseIds.has(item.id)) {
    collectionIds = ["technical-writing"];
  }

  return {
    ...item,
    achievementStatus: investigationCaseIds.has(item.id)
      ? "INVESTIGATED"
      : item.achievementStatus,
    projectId,
    collectionIds,
  };
});

const removedAggregateClaimIds = new Set([
  "claim-public-l-05",
  "claim-public-l-06",
  "claim-public-l-07",
]);
const removedProjectIds = new Set([
  "context-engineering-project",
  "technical-writing-project",
  "engineering-delivery-learning-project",
]);

const roleResetProjectClaim = {
  id: "claim-role-reset-tool-project-delivered",
  subjectType: "PROJECT",
  subjectId: "role-reset-tool-project",
  category: "OUTCOME",
  statement:
    "测试角色重置工具已形成查询、确认、重置、复查和使用说明组成的交付闭环。",
  detail:
    "项目级表述由同一案例的脱敏验收材料支持，不声明精确使用量或效率提升。",
  achievementStatus: "DELIVERED",
  contributionType: "PRIMARY",
  verificationBasis: "EVIDENCE_SUPPORTED",
  verificationStatus: "VERIFIED",
  materiality: "KEY",
  topics: ["TEST_TOOLING", "DELIVERY"],
  audiencePriorities: {
    INTERVIEWER: 100,
    MENTOR: 100,
    HR: 80,
    GUEST: 70,
  },
};

const claims = portfolio.claims
  .filter((claim) => !removedAggregateClaimIds.has(claim.id))
  .map((claim) => {
    if (!investigationCaseIds.has(claim.subjectId)) {
      return claim;
    }
    return {
      ...claim,
      category: "OUTCOME",
      achievementStatus: "INVESTIGATED",
      topics: [
        ...new Set(
          claim.topics.map((topic) =>
            topic === "LEARNING" ? "INVESTIGATED" : topic,
          ),
        ),
      ],
    };
  })
  .concat(roleResetProjectClaim);

const claimEvidenceLinks = portfolio.claimEvidenceLinks
  .filter((link) => !removedAggregateClaimIds.has(link.claimId))
  .concat({
    id: "link-role-reset-tool-project-delivered",
    claimId: "claim-role-reset-tool-project-delivered",
    evidenceId: "evidence-case-role-reset-guide-and-acceptance",
    supportType: "DIRECT",
    scope: "支持测试角色重置工具的交付闭环，不支持精确使用量推断。",
    reviewStatus: "APPROVED",
  });
const evidence = portfolio.evidence;

const timelineEvents = portfolio.timelineEvents.map((event) => {
  const projectIds = event.projectIds.filter(
    (projectId) => !removedProjectIds.has(projectId),
  );
  const claimIds = event.claimIds.filter(
    (claimId) => !removedAggregateClaimIds.has(claimId),
  );
  if (event.id === "timeline-case-role-reset-delivery") {
    projectIds.push("role-reset-tool-project");
    claimIds.push("claim-role-reset-tool-project-delivered");
  }
  return {
    ...event,
    projectIds,
    claimIds,
    evidenceIds: event.evidenceIds,
  };
});

const questionPresets = portfolio.questionPresets.map((question) => ({
  ...question,
  projectIds: question.projectIds.filter(
    (projectId) => !removedProjectIds.has(projectId),
  ),
}));

const migratedPortfolio = {
  ...portfolio,
  schemaVersion: "4.0",
  contentVersion: "2026-07-29.1",
  projects: [
    sqlProject,
    activityProject,
    roleResetProject,
    agentProject,
    imageProject,
  ],
  claims,
  claimEvidenceLinks,
  questionPresets,
  evidence,
  timelineEvents,
  cases,
  collections,
};

fs.writeFileSync(
  portfolioPath,
  `${JSON.stringify(migratedPortfolio, null, 2)}\n`,
  "utf8",
);
