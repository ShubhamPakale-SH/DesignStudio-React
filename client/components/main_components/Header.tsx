const Header = () => {
  return (
    <header className="w-full bg-neutral-900 text-neutral-100">
      <div className="mx-auto max-w-7xl px-4 py-3 flex items-center justify-between bg-white text-[#2596BE]">
        <span className="text-sm sm:text-base font-semibold tracking-[0.4px]">Design Studio</span>
        {/* User Icon */}
        <img 
          src="https://via.placeholder.com/42" 
          alt="User Icon" 
          className="w-10 h-10 rounded-full cursor-pointer"
        />
      </div>
    </header>
  );
};

export default Header;