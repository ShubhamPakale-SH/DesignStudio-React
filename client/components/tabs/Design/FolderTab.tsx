import { useEffect, useMemo, useState } from "react";
import { fetchFormDesignGroupList } from "@/service/Folder/FolderService";
import DataTable, {
  type DataTableColumn,
} from "@/components/Reusable Components/DataTable";
import { Input } from "@/components/ui/input";
import { TableHead, TableRow } from "@/components/ui/table";

type RowRecord = Record<string, any>;

const FolderTab = () => {
  const [data, setData] = useState<unknown>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [searchInput, setSearchInput] = useState("");
  const [searchTerm, setSearchTerm] = useState("");

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

  const filteredRows = useMemo(() => {
    const q = searchTerm.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((r) => {
      const name = (
        r.FormDesignGroupName ?? r.formDesignGroupName ?? r.FormGroupName ?? r.name ?? ""
      ) as string;
      return String(name).toLowerCase().includes(q);
    });
  }, [rows, searchTerm]);

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

  const headerBelowRow = (
    <TableRow className="bg-slate-50 hover:bg-slate-50">
      <TableHead>
        <Input
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") setSearchTerm(searchInput);
          }}
          placeholder="Search folders and press Enter"
        />
      </TableHead>
    </TableRow>
  );

  return (
    <div className="text-sm text-neutral-700 w-full">
      {loading && <p>Loading…</p>}
      {error && <p className="text-red-600">{error}</p>}
      {!loading && !error && (
        <DataTable
          columns={columns}
          data={filteredRows}
          caption="Form Design Groups"
          emptyMessage="No groups found"
          rowKey={rowKey}
          striped
          headerBelowRow={headerBelowRow}
        />
      )}
    </div>
  );
};

export default FolderTab;
