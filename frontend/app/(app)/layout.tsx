import { AppShell } from "@/components/shell/AppShell";

/**
 * 应用区布局
 * 所有功能页共用外壳：单层侧边导航 + 毛玻璃顶栏 + ⌘K 命令面板 + 任务抽屉。
 * 注意：路由**不含** teamId / projectId —— 上下文由顶部切换器驱动，
 * 这既让导航保持一层，也让全站可做静态导出。
 */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return <AppShell>{children}</AppShell>;
}
