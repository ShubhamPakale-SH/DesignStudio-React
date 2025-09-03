import { User } from "lucide-react";

const Header = () => {
  return (
    <header className="w-full bg-neutral-900 text-neutral-100">
      <div className="mx-auto max-w-7xl px-4 py-3 flex items-center justify-between bg-white text-[#2596BE]">
        <span className="text-sm sm:text-base font-semibold tracking-[0.4px]">Design Studio</span>
        <button
          type="button"
          aria-label="User account"
          className="inline-flex items-center justify-center rounded-full p-2 hover:bg-neutral-100 focus:outline-none focus:ring-2 focus:ring-[#2596BE]/40"
        >
          <User className="h-6 w-6" />
        </button>
      </div>
    </header>
  );
};

export default Header;
