"use client";

import * as React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "@/components/ui/toaster";
import { useThemeStore } from "@/store/theme";

export function Providers({ children }: { children: React.ReactNode }) {
  // 每个会话创建一个 QueryClient（避免 SSR/CSR 间共享状态）
  const [client] = React.useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            refetchOnWindowFocus: false,
            retry: 1,
          },
        },
      })
  );

  // 应用持久化主题
  React.useEffect(() => {
    useThemeStore.getState().apply();
  }, []);

  return (
    <QueryClientProvider client={client}>
      {children}
      <Toaster />
    </QueryClientProvider>
  );
}
