import { ReactNode } from "react";
import { cn } from "@/lib/utils";

interface RectangleBoxProps {
  children?: ReactNode;
  className?: string;
}

const RectangleBox = ({ children, className }: RectangleBoxProps) => {
  return (
    <div
      className={cn(
        "rounded-md bg-white p-4",
        // Soft all-around shadow + subtle border for light/dark themes
        "shadow-[0_0_0_1px_rgba(0,0,0,0.06),0_8px_24px_rgba(0,0,0,0.08),0_2px_8px_rgba(0,0,0,0.06)]",
        "dark:bg-neutral-900 dark:shadow-[0_0_0_1px_rgba(255,255,255,0.08),0_8px_24px_rgba(0,0,0,0.6),0_2px_8px_rgba(0,0,0,0.5)]",
        className,
      )}
    >
      {children}
    </div>
  );
};

export default RectangleBox;
