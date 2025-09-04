import { useEffect, useState } from "react";
import { fetchFormDesignGroupList } from "@/service/Folder/FolderService";

const FolderTab = () => {
  const [data, setData] = useState<unknown>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const run = async () => {
      try {
        setLoading(true);
        const res = await fetchFormDesignGroupList();
        setData(res);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Request failed");
      } finally {
        setLoading(false);
      }
    };
    run();
  }, []);

  return (
    <div className="text-sm text-neutral-700 w-full">
      {loading && <p>Loading…</p>}
      {error && <p className="text-red-600">{error}</p>}
      {!loading && !error && (
        <pre className="text-xs whitespace-pre-wrap break-all bg-muted/30 p-3 rounded-md overflow-auto max-h-80">
          {JSON.stringify(data, null, 2)}
        </pre>
      )}
    </div>
  );
};

export default FolderTab;
