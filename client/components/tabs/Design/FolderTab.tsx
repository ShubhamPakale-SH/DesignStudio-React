import { useEffect, useMemo, useState } from "react";
import { fetchFormDesignGroupList } from "@/service/Folder/FolderService";
import DataTable, {
  type DataTableColumn,
} from "@/components/Reusable Components/DataTable";

type RowRecord = Record<string, any>;

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

  const rows: RowRecord[] = useMemo(() => {
    const payload = data as any;
    if (Array.isArray(payload)) return payload as RowRecord[];
    if (payload && Array.isArray(payload.rows))
      return payload.rows as RowRecord[];
    return [];
  }, [data]);

  const columns: DataTableColumn<RowRecord>[] = useMemo(() => {
    return [
      {
        key: "FormDesignGroupName",
        header: "Folder Name",
        render: (row) =>
          (row.FormDesignGroupName ??
            row.formDesignGroupName ??
            row.FormGroupName ??
            row.name ??
            "") as string,
      },
    ];
  }, []);

  const rowKey = (row: RowRecord, index: number) =>
    (row.FormGroupId ?? row.FormGroupID ?? row.id ?? index) as string | number;

  return (
    <div className="text-sm text-neutral-700 w-full">
      {loading && <p>Loading…</p>}
      {error && <p className="text-red-600">{error}</p>}
      {!loading && !error && (
        <DataTable
          columns={columns}
          data={rows}
          caption="Form Design Groups"
          emptyMessage="No groups found"
          rowKey={rowKey}
          striped
        />
      )}
    </div>
  );
};

export default FolderTab;
