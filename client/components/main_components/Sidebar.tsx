import { useState } from "react";
import {
  Menu,
  X,
  RotateCcw,
  LayoutGrid,
  FileText,
  BarChart3,
  Settings,
} from "lucide-react";

const items = [
  { label: "Dashboard", icon: RotateCcw },
  { label: "Design", icon: LayoutGrid, active: true },
  { label: "Rules Manager", icon: FileText },
  { label: "Extended Hangfire", icon: BarChart3 },
  { label: "Configuration", icon: Settings },
];

const Sidebar = () => {
  const [open, setOpen] = useState(false);

  return (
    <aside
      className={`fixed inset-y-0 left-0 z-40 bg-[#073a50] text-slate-200 border-r border-black/20 transition-all duration-300 shadow-lg ${
        open ? "w-72" : "w-16"
      }`}
    >
      <div className="flex items-center gap-3 px-3 py-3 border-b border-white/10">
        <button
          type="button"
          aria-label={open ? "Close Menu" : "Open Menu"}
          onClick={() => setOpen((v) => !v)}
          className="inline-flex h-8 w-8 items-center justify-center rounded-md hover:bg-white/10 focus:outline-none focus:ring-2 focus:ring-white/30"
        >
          {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>
        {open && <span className="text-sm font-semibold">Close Menu</span>}
      </div>

      <nav className="p-2 space-y-2">
        {items.map(({ label, icon: Icon, active }) => (
          <button
            key={label}
            className={`group w-full flex items-center gap-3 rounded-lg px-3 py-2 text-left transition-colors ${
              active
                ? "bg-[#0b5b7a] text-white"
                : "text-slate-300 hover:bg-white/10"
            }`}
            title={label}
          >
            <Icon className="h-5 w-5 opacity-90" />
            <span className={`${open ? "block" : "hidden"} text-sm`}>{label}</span>
          </button>
        ))}
      </nav>
    </aside>
  );
};

export default Sidebar;
