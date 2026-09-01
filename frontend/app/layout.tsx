import type { Metadata, Viewport } from "next";
import "./globals.css";
import { Providers } from "./providers";

export const metadata: Metadata = {
  title: "Pulse 脉搏 · 任务管理",
  description: "面向 5-50 人小团队的轻量任务协作工具",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
};

/**
 * 主题防闪烁：在首屏绘制前同步应用 .dark，
 * 避免深色用户看到白色闪一下（FOUC）。
 */
const themeScript = `
(function(){
  try {
    var raw = localStorage.getItem('pulse-theme');
    var mode = raw ? JSON.parse(raw).state.mode : 'light';
    var dark = mode === 'dark' || (mode === 'system' &&
      window.matchMedia('(prefers-color-scheme: dark)').matches);
    if (dark) document.documentElement.classList.add('dark');
    document.documentElement.style.colorScheme = dark ? 'dark' : 'light';
  } catch (e) {}
})();
`;

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body className="min-h-screen bg-canvas text-fg antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
