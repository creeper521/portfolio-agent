import { describe, expect, it } from 'vitest'

import type { PublicProject } from '../../public-content/model/publicContentTypes'
import { previewPublicContent } from '../../public-content/data/previewPublicContent'

import {
  CAREER_TRACK_LABEL,
  PROJECT_NATURE_LABEL,
  buildProjectMainlines,
} from './projectMainlineModel'

/** 造一个最小可用的 project，字段默认值可被覆盖。 */
function makeProject(overrides: Partial<PublicProject> = {}): PublicProject {
  return {
    id: 'project-x',
    slug: 'project-x',
    code: 'P-XX',
    title: '占位项目',
    summary: '占位摘要',
    background: '占位背景',
    responsibilities: [],
    solution: '',
    keyDecisions: [],
    technologies: [],
    verification: [],
    outcome: '',
    handoff: '',
    status: 'DELIVERED',
    contributionType: 'PRIMARY',
    careerTrack: 'JAVA_BACKEND',
    projectNature: 'TOOL',
    displayTier: 'PRIMARY',
    caseCount: 0,
    featuredCases: [],
    evidenceIds: [],
    evidence: [],
    suggestedQuestions: [],
    ...overrides,
  }
}

describe('projectMainlineModel · buildProjectMainlines', () => {
  it('Java 后端与 Agent 项目分别进入各自主线组，Java 在前', () => {
    const projects = [
      makeProject({ slug: 'agent-mvp', careerTrack: 'AGENT', projectNature: 'INTEGRATION_PROTOTYPE' }),
      makeProject({ slug: 'sql-audit', careerTrack: 'JAVA_BACKEND' }),
    ]
    const groups = buildProjectMainlines(projects)
    expect(groups.map((g) => g.key)).toEqual(['java', 'agent'])
    expect(groups[0].projects.map((p) => p.slug)).toEqual(['sql-audit'])
    expect(groups[1].projects.map((p) => p.slug)).toEqual(['agent-mvp'])
  })

  it('displayTier 为 SECONDARY 的项目进入次级项目组，排最后', () => {
    const projects = [
      makeProject({ slug: 'image-upload', displayTier: 'SECONDARY' }),
      makeProject({ slug: 'sql-audit' }),
    ]
    const groups = buildProjectMainlines(projects)
    expect(groups.map((g) => g.key)).toEqual(['java', 'secondary'])
    expect(groups[1].projects.map((p) => p.slug)).toEqual(['image-upload'])
  })

  it('careerTrack 全部 UNCLASSIFIED 时回退为单一「项目」分区', () => {
    const projects = [
      makeProject({ slug: 'p1', careerTrack: 'UNCLASSIFIED' }),
      makeProject({ slug: 'p2', careerTrack: 'UNCLASSIFIED' }),
    ]
    const groups = buildProjectMainlines(projects)
    expect(groups.map((g) => g.key)).toEqual(['general'])
    expect(groups[0].label).toBe('项目')
    expect(groups[0].projects).toHaveLength(2)
  })

  it('乱序输入仍按 java → agent → general → secondary 固定顺序输出', () => {
    const projects = [
      makeProject({ slug: 'sec', displayTier: 'SECONDARY' }),
      makeProject({ slug: 'gen', careerTrack: 'UNCLASSIFIED' }),
      makeProject({ slug: 'agent', careerTrack: 'AGENT' }),
      makeProject({ slug: 'java', careerTrack: 'JAVA_BACKEND' }),
    ]
    const groups = buildProjectMainlines(projects)
    expect(groups.map((g) => g.key)).toEqual(['java', 'agent', 'general', 'secondary'])
  })

  it('组内保持输入顺序（策展顺序由内容决定，不做额外排序）', () => {
    const projects = [
      makeProject({ slug: 'first' }),
      makeProject({ slug: 'second' }),
      makeProject({ slug: 'third' }),
    ]
    const groups = buildProjectMainlines(projects)
    expect(groups[0].projects.map((p) => p.slug)).toEqual(['first', 'second', 'third'])
  })

  it('空数组返回空数组', () => {
    expect(buildProjectMainlines([])).toEqual([])
  })

  it('分组架位编号按输出顺序从 01 递增', () => {
    const projects = [
      makeProject({ slug: 'java' }),
      makeProject({ slug: 'agent', careerTrack: 'AGENT' }),
      makeProject({ slug: 'sec', displayTier: 'SECONDARY' }),
    ]
    const groups = buildProjectMainlines(projects)
    expect(groups.map((g) => g.code)).toEqual(['01', '02', '03'])
  })

  it('对 previewPublicContent 的真实项目产生 Java 主线组', () => {
    const groups = buildProjectMainlines(previewPublicContent.projects)
    expect(groups.map((g) => g.key)).toEqual(['java'])
    expect(groups[0].projects.map((p) => p.slug)).toContain('sql-audit')
  })
})

describe('projectMainlineModel · 文案映射', () => {
  it('CAREER_TRACK_LABEL 覆盖全部 CareerTrack', () => {
    expect(CAREER_TRACK_LABEL.JAVA_BACKEND).toBe('Java 后端')
    expect(CAREER_TRACK_LABEL.AGENT).toBe('Agent')
    expect(CAREER_TRACK_LABEL.UNCLASSIFIED).toBeTruthy()
  })

  it('PROJECT_NATURE_LABEL 覆盖全部 ProjectNature', () => {
    expect(PROJECT_NATURE_LABEL.TOOL).toBe('工具')
    expect(PROJECT_NATURE_LABEL.WORKSTREAM).toBe('工作主线')
    expect(PROJECT_NATURE_LABEL.INTEGRATION_PROTOTYPE).toBe('集成原型')
    expect(PROJECT_NATURE_LABEL.UNCLASSIFIED).toBeTruthy()
  })
})
