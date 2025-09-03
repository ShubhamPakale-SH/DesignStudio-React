import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";

const documentDesigns = [
  { id: "D001", name: "Anchor Document Template", version: "2.1", status: "Active" },
  { id: "D002", name: "MasterList Financial Q3", version: "1.0", status: "Active" },
  { id: "D003", name: "Collateral Marketing Brochure", version: "3.5", status: "In Review" },
  { id: "D004", name: "View Only - Annual Report", version: "1.2", status: "Archived" },
];

const DesignCompileTab = () => {
  return (
    <div className="w-full space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-neutral-800">
          Document Design List
        </h3>
        <div className="w-64">
          <Input type="search" placeholder="Search designs..." />
        </div>
      </div>

      <div className="border rounded-md">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="font-bold">Design Name</TableHead>
              <TableHead className="w-[100px] font-bold">Version</TableHead>
              <TableHead className="w-[120px] font-bold">Status</TableHead>
              <TableHead className="text-right w-[150px] font-bold">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {documentDesigns.map((design) => (
              <TableRow key={design.id}>
                <TableCell className="font-medium">{design.name}</TableCell>
                <TableCell>{design.version}</TableCell>
                <TableCell>
                  <span
                    className={`px-2 py-1 text-xs font-semibold rounded-full ${
                      design.status === "Active"
                        ? "bg-green-100 text-green-800"
                        : design.status === "In Review"
                        ? "bg-yellow-100 text-yellow-800"
                        : "bg-gray-100 text-gray-800"
                    }`}
                  >
                    {design.status}
                  </span>
                </TableCell>
                <TableCell className="text-right">
                  <Button variant="outline" size="sm">
                    View
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  );
};

export default DesignCompileTab;