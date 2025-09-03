import { User } from "lucide-react";

interface HeaderProps {
  sidebarOpen: boolean;
}

const Header = ({ sidebarOpen }: HeaderProps) => {
  return (
    <header
      className={`w-full bg-neutral-900 text-neutral-100 transition-[padding-left] duration-300 ease-in-out`}
      style={{ paddingLeft: sidebarOpen ? "18rem" : "4rem" }}
    >
      <div className="mx-auto max-w-7xl px-4 py-3 flex items-center justify-between bg-white text-[#2596BE]">
        <span className="text-sm sm:text-base font-semibold tracking-[0.4px]">
          Design Studio
        </span>
        <button
          type="button"
          aria-label="User account"
          className="flex items-center justify-center rounded-full w-[39px] px-2 py-1 bg-transparent border-0"
        >
          <User
            className="flex h-[21px] w-[21px] text-[#4A4A4A] bg-white rounded-[9px] overflow-hidden flex-col justify-center items-center pt-1 border border-[#4A4A4A]"
            style={{ stroke: "rgb(37, 150, 190)" }}
            aria-hidden
          />
        </button>
      </div>
    </header>
  );
};

export default Header;
