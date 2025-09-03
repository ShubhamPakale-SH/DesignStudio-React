import { cn } from "@/lib/utils";

export interface DesignListProps {
  designs: string[];
  className?: string;
  selected?: string | null;
  onSelect?: (name: string) => void;
}

const DesignList = ({ designs, className, selected, onSelect }: DesignListProps) => {
  return (
    <div
      className={cn(
        "w-full overflow-hidden rounded-md border border-border bg-background text-foreground shadow-[0_0_0_1px_rgba(0,0,0,0.06),0_8px_24px_rgba(0,0,0,0.08),0_2px_8px_rgba(0,0,0,0.06)] dark:shadow-[0_0_0_1px_rgba(255,255,255,0.08),0_8px_24px_rgba(0,0,0,0.6),0_2px_8px_rgba(0,0,0,0.5)]",
        className,
      )}
      role="region"
      aria-label="Design list"
    >
      <table className="w-full table-fixed border-collapse">
        <thead>
          <tr className="bg-muted/60">
            <th scope="col" className="text-left px-4 py-2 text-sm font-semibold">Design Name</th>
          </tr>
        </thead>
        <tbody>
          {designs.map((name) => (
            <tr
              key={name}
              className={cn(
                "border-t border-border/60",
                selected === name ? "bg-accent/50" : "hover:bg-muted/40",
              )}
            >
              <td className="px-4 py-2 text-sm">
                {onSelect ? (
                  <button
                    type="button"
                    onClick={() => onSelect(name)}
                    className={cn(
                      "w-full text-left outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-sm",
                      selected === name ? "font-semibold" : "font-normal",
                    )}
                  >
                    {name}
                  </button>
                ) : (
                  name
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default DesignList;
