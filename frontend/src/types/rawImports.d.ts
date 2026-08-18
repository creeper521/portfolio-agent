// Vite `?raw` 原始源码导入的类型声明（仅测试用于断言组件 CSS 合同，
// 如推荐窄屏单列 media query 与 prefers-reduced-motion）。
declare module '*.vue?raw' {
  const componentSource: string
  export default componentSource
}
