import { useCallback, useEffect, useMemo, useState } from "react";
import {
  fetchFormDesignGroupList,
  fetchFormGroupMappingList,
} from "@/service/Folder/FolderService";
import DataTable, {
  type DataTableColumn,
} from "@/components/Reusable Components/DataTable";
import { Input } from "@/components/ui/input";
import { TableHead, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { RefreshCw, Plus, Pencil } from "lucide-react";

type RowRecord = Record<string, any>;

const FolderTab = () => {
  const [data, setData] = useState<unknown>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [searchInput, setSearchInput] = useState("");
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedKey, setSelectedKey] = useState<string | number | null>(null);
  const [selectedName, setSelectedName] = useState<string>("");
  const [mapLoading, setMapLoading] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);
  const [mapRows, setMapRows] = useState<RowRecord[]>([]);

  const refetch = useCallback(async () => {
    try {
      setLoading(true);
      const res = await fetchFormDesignGroupList();
      setData(res);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Request failed");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refetch();
  }, [refetch]);

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
      const name = (r.FormDesignGroupName ??
        r.formDesignGroupName ??
        r.FormGroupName ??
        r.name ??
        "") as string;
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

  const handleSelectRow = async (row: RowRecord, index: number) => {
    const key = rowKey(row, index);
    setSelectedKey(key);
    const name = (row.FormDesignGroupName ??
      row.formDesignGroupName ??
      row.FormGroupName ??
      row.name ??
      "") as string;
    setSelectedName(name);
    const id = row.FormGroupId ?? row.FormGroupID ?? row.id ?? key;
    try {
      setMapLoading(true);
      setMapError(null);
      const res: any = await fetchFormGroupMappingList(id);
      const rows = Array.isArray(res)
        ? res
        : Array.isArray(res?.rows)
          ? res.rows
          : [];
      setMapRows(rows);
    } catch (e) {
      setMapError(e instanceof Error ? e.message : "Request failed");
      setMapRows([]);
    } finally {
      setMapLoading(false);
    }
  };

  const headerBelowRow = (
    <TableRow className="bg-slate-50 hover:bg-slate-50">
      <TableHead>
        <Input
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") setSearchTerm(searchInput);
          }}
          placeholder="Search folders"
        />
      </TableHead>
    </TableRow>
  );

  return (
    <div className="text-sm text-neutral-700 w-full">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-base font-semibold">Document Folder List</h3>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="icon"
            onClick={refetch}
            aria-label="Refresh"
          >
            <RefreshCw className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="icon" aria-label="Add">
            <Plus className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="icon" aria-label="Edit">
            <Pencil className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <div className="w-full flex flex-col md:flex-row gap-6 px-2">
        <div className="md:w-5/12 w-full pr-4">
          {loading && <p>Loading…</p>}
          {error && <p className="text-red-600">{error}</p>}
          {!loading && !error && (
            <DataTable
              columns={columns}
              data={filteredRows}
              emptyMessage="No groups found"
              rowKey={rowKey}
              striped
              headerBelowRow={headerBelowRow}
              onRowClick={handleSelectRow}
              rowClassName={(row, index) =>
                String(rowKey(row, index)) === String(selectedKey)
                  ? "bg-sky-100"
                  : undefined
              }
            />
          )}
        </div>

        <div className="md:w-7/12 w-full md:mr-8 pr-8">
          <div className="flex items-center justify-between mb-2">
            <h4 className="text-sm font-semibold">
              Document Folder List{selectedName ? ` - ${selectedName}` : ""}
            </h4>
            <Button className="h-8" size="sm">
              Save
            </Button>
          </div>

          {mapLoading && <p>Loading…</p>}
          {mapError && <p className="text-red-600">{mapError}</p>}
          {!mapLoading && !mapError && (
            <DataTable
              columns={[
                {
                  key: "FormDesignName",
                  header: "Document Design Name",
                  render: (r) =>
                    (r.FormDesignName ??
                      r.DocumentDesignName ??
                      r.documentDesignName ??
                      "") as string,
                },
                {
                  key: "Abbreviation",
                  header: "Abbreviation",
                  render: (r) =>
                    (r.Abbreviation ?? r.abbreviation ?? "") as string,
                },
                {
                  key: "AllowMultipleInstance",
                  header: "Multiple Instance",
                  render: (r) =>
                    (r.AllowMultipleInstance ??
                    r.MultipleInstance ??
                    r.isMultipleInstance ??
                    false)
                      ? "Yes"
                      : "No",
                },
                {
                  key: "IsIncluded",
                  header: "Include",
                  render: (r) =>
                    (r.IsIncluded ?? r.Include ?? r.include ?? false)
                      ? "Yes"
                      : "No",
                },
              ]}
              data={mapRows}
              emptyMessage={
                selectedName
                  ? "No mappings found"
                  : "Select a folder to view mappings"
              }
              rowKey={(r, i) =>
                (r.FormDesignId ?? r.DocumentDesignID ?? r.id ?? i) as
                  | string
                  | number
              }
              striped
            />
          )}
        </div>
      </div>
    </div>
  );
};

export default FolderTab;
