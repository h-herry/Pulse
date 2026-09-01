"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

/** 根路径：直接进入总览，不再需要「先选团队再选项目」的中转页 */
export default function HomePage() {
  const router = useRouter();

  useEffect(() => {
    router.replace("/overview");
  }, [router]);

  return null;
}
