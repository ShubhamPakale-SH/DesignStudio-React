import { useState, useMemo } from "react";
import Header from "@/components/main_components/Header";
import Sidebar from "@/components/main_components/Sidebar";
import Footer from "@/components/main_components/Footer";
import Dashboard from "@/components/tabs/Dashboard/Dashboard";
import Design from "@/components/tabs/Design/Design";
import RulesManager from "@/components/tabs/RulesManager/RulesManager";
import ExtendedHangfire from "@/components/tabs/ExtendedHangfire/ExtendedHangfire";
import Configuration from "@/components/tabs/Configuration/Configuration";

const tabs = [
  "Dashboard",
  "Design",
  "Rules Manager",
  "Extended Hangfire",
  "Configuration",
] as const;

type Tab = (typeof tabs)[number];

export default function Index() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [selected, setSelected] = useState<Tab>("Design");

  const Content = useMemo(() => {
    switch (selected) {
      case "Dashboard":
        return Dashboard;
      case "Design":
        return Design;
      case "Rules Manager":
        return RulesManager;
      case "Extended Hangfire":
        return ExtendedHangfire;
      case "Configuration":
        return Configuration;
    }
  }, [selected]);

  return (
    <div className="min-h-screen flex flex-col bg-background text-foreground">
      <Header sidebarOpen={sidebarOpen} />
      <div className="flex flex-1 min-h-0">
        <Sidebar
          open={sidebarOpen}
          onToggle={() => setSidebarOpen((v) => !v)}
          selected={selected}
          onSelect={(label) => setSelected(label as Tab)}
        />
        <main
          className="flex-1 flex items-center justify-center shadow-[1px_1px_3px_0_rgba(0,0,0,1)] transition-[padding-left] duration-300 ease-in-out"
          style={{ paddingLeft: sidebarOpen ? "18rem" : "4rem" }}
        >
          <Content />
        </main>
      </div>
      <Footer />
    </div>
  );
}
