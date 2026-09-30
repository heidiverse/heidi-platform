// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { useLocalStorage } from "usehooks-ts";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { Header } from "@/components/layout/header";
import { SidebarProvider } from "@/components/ui/sidebar";

export function Layout({ children }: { children?: ReactNode }) {
  const [open, setOpen] = useLocalStorage("sidebar:state", true);

  return (
    <SidebarProvider open={open} onOpenChange={setOpen}>
      <AppSidebar />
      <main className="@container w-full">
        <div className="mx-auto flex h-full w-full max-w-6xl min-w-0 flex-col px-4 pb-8 sm:px-8">
          <Header />
          {children}
        </div>
      </main>
    </SidebarProvider>
  );
}
