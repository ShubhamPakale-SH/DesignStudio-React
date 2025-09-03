const Header = () => {
  return (
    <header className="w-full bg-neutral-900 text-neutral-100">
      <div className="mx-auto max-w-7xl px-4 py-3 flex items-center justify-between bg-white text-[#2596BE]">
        <span className="text-sm sm:text-base font-semibold tracking-[0.4px]">Design Studio</span>
      </div>
      <IconButton edge="end" color="inherit">
<AccountCircleIcon />
</IconButton>
    </header>
  );
};

export default Header;
