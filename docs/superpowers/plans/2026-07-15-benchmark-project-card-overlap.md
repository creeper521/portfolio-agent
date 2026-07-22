# Benchmark Project Card Overlap Implementation Plan

> **执行状态（2026-07-20）：** 已完成。该计划只作用于历史设计探索原型，不是当前生产前端待办。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent terminal mock text from colliding with exposed project metadata while preserving the alternating featured-card layout.

**Architecture:** Make one scoped CSS change in the standalone HTML. The visual and content grid overlap remains; only the terminal mock's usable width and alignment change.

**Tech Stack:** HTML, CSS Grid, Playwright browser evaluation

## Global Constraints

- Modify only the project-card terminal mock layout and the two approved design records.
- Do not change project copy, status, interactions, or repository history.
- Do not commit without explicit authorization.

---

### Task 1: Project-card mock safety zone

**Files:**
- Modify: `design/huashu-portfolio/design-demos/benchmark-reference.html`
- Test: one-off Playwright DOM collision check

**Interfaces:**
- Consumes: `.project`, `.visual`, `.mock-head`, `.mock-body`, and `.content` DOM structure.
- Produces: a 65% terminal-content region aligned away from the content overlap.

- [x] **Step 1: Run the failing browser check**

  At 1440×900, compare `.mock-body` with `.over`, `.badge`, `.p-title`, `.tags`, and `.links`. Expected before the fix: collisions in all four project cards.

- [x] **Step 2: Add the minimal CSS safety zone**

  Limit `.mock-head` and `.mock-body` to 65% width and use `margin-left: auto` for project cards whose visual is on the right.

- [x] **Step 3: Run the browser check at all target viewports**

  Expected at 1440×900, 1024×768, 768×1024, and 390×844: zero collisions.

- [x] **Step 4: Capture and inspect the repaired project section**

  Expected: no text collision, preserved alternating layout, and zero page errors.
