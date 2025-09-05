import { useState } from "react";
import RectangleBox from "@/components/Reusable Components/RectangleBox";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import DocumentsTab from "@/components/tabs/Design/DocumentsTab";
import FolderTab from "@/components/tabs/Design/FolderTab";
import DesignCompileTab from "@/components/tabs/Design/DesignCompileTab";
import DesignSyncTab from "@/components/tabs/Design/DesignSyncTab";
import { useEffect } from "react";
import { fetchDesignTypes, type DesignType } from "@/service/Design/DesignService";

const Design = () => {
  const [value, setValue] = useState("documents");
  const [designTypes, setDesignTypes] = useState<DesignType[]>([]);

  useEffect(() => {
    let ignore = false;
    const loadDesignTypes = async () => {
      if (value !== "documents" || designTypes.length > 0) return;
      try {
        const types = await fetchDesignTypes();
        if (!ignore) setDesignTypes(types);
      } catch (e) {
        console.error("Design types fetch failed", e);
      }
    };
    loadDesignTypes();
    return () => {
      ignore = true;
    };
  }, [value, designTypes.length]);

  return (
    <div className="w-full h-full flex items-start justify-start py-6 px-0 flex-row flex-wrap">
      <RectangleBox className="w-full flex flex-row justify-start mx-[10px] mt-2 pr-6 pl-4">
        <Tabs value={value} onValueChange={setValue}>
          <div className="flex items-center justify-between">
            <h2 className="text-[20px] leading-7 font-semibold">Design</h2>
          </div>
          <TabsList className="mt-3 w-[520px] grid grid-cols-4">
            <TabsTrigger value="documents">Documents</TabsTrigger>
            <TabsTrigger value="folder">Folder</TabsTrigger>
            <TabsTrigger value="compile">Design Compile</TabsTrigger>
            <TabsTrigger value="sync">Design Sync</TabsTrigger>
          </TabsList>

          <TabsContent value="documents" className="pt-4">
            <DocumentsTab designTypes={designTypes} />
          </TabsContent>
          <TabsContent value="folder" className="pt-4">
            <FolderTab />
          </TabsContent>
          <TabsContent value="compile" className="pt-4">
            <DesignCompileTab />
          </TabsContent>
          <TabsContent value="sync" className="pt-4">
            <DesignSyncTab />
          </TabsContent>
        </Tabs>
      </RectangleBox>
    </div>
  );
};
export default Design;
