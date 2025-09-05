import * as React from "react";
import { cn } from "@/lib/utils";
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

export type DataTableColumn<T> = {
  key: keyof T | string;
  header: React.ReactNode;
  className?: string;
  render?: (row: T, index: number) => React.ReactNode;
};

export type DataTableProps<T> = {
  columns: DataTableColumn<T>[];
  data: T[];
  caption?: React.ReactNode;
  emptyMessage?: React.ReactNode;
  rowKey?: (row: T, index: number) => string | number;
  className?: string;
  headerRowClassName?: string;
  rowClassName?: (row: T, index: number) => string | undefined;
  striped?: boolean;
  headerBelowRow?: React.ReactNode;
  onRowClick?: (row: T, index: number) => void;
};

function defaultRowKey<T>(row: T, index: number): string | number {
  return (row as any)?.id ?? index;
}

export default function DataTable<T extends Record<string, any>>({
  columns,
  data,
  caption,
  emptyMessage = "No data to display",
  rowKey = defaultRowKey,
  className,
  headerRowClassName,
  rowClassName,
  striped = false,
  headerBelowRow,
  onRowClick,
}: DataTableProps<T>) {
  return (
    <div className={cn("w-full", className)}>
      <div className="border rounded-md">
        <Table>
          {caption ? <TableCaption>{caption}</TableCaption> : null}
          <TableHeader>
            <TableRow className={headerRowClassName}>
              {columns.map((col, i) => (
                <TableHead key={String(col.key) + i} className={col.className}>
                  {col.header}
                </TableHead>
              ))}
            </TableRow>
            {headerBelowRow}
          </TableHeader>
          <TableBody>
            {data.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={columns.length}
                  className="h-24 text-center text-muted-foreground"
                >
                  {emptyMessage}
                </TableCell>
              </TableRow>
            ) : (
              data.map((row, rowIndex) => (
                <TableRow
                  key={String(rowKey(row, rowIndex))}
                  className={cn(
                    striped && rowIndex % 2 === 1 ? "bg-slate-50" : undefined,
                    rowClassName?.(row, rowIndex),
                  )}
                >
                  {columns.map((col, colIndex) => (
                    <TableCell
                      key={String(col.key) + colIndex}
                      className={col.className}
                    >
                      {col.render
                        ? col.render(row, rowIndex)
                        : String((row as any)[col.key as any] ?? "")}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
