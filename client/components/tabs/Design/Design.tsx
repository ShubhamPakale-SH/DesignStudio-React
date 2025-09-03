import { useState } from "react";
import RectangleBox from "@/components/Reusable Components/RectangleBox";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

const Design = () => {
  const [value, setValue] = useState("documents");

  return (
    <div className="w-full h-full flex items-start justify-start py-6 px-0 flex-row flex-wrap">
     <RectangleBox className="w-full flex flex-row justify-start mx-[10px] mt-2 pr-6 pl-4">
        <Tabs value={value} onValueChange={setValue}>
          <div className="flex items-center justify-between">
            <h2 className="text-[20px] leading-7 font-semibold">Design</h2>
          </div>
          <TabsList className="mt-3 w-full grid grid-cols-4">
            <TabsTrigger value="documents">Documents</TabsTrigger>
            <TabsTrigger value="folder">Folder</TabsTrigger>
            <TabsTrigger value="compile">Design Compile</TabsTrigger>
            <TabsTrigger value="sync">Design Sync</TabsTrigger>
          </TabsList>

          <TabsContent value="documents" className="pt-4">
            <p className="text-sm text-neutral-700">Documents content</p>
          </TabsContent>
          <TabsContent value="folder" className="pt-4">
            <p className="text-sm text-neutral-700">Folder content</p>
          </TabsContent>
          <TabsContent value="compile" className="pt-4">
            <p className="text-sm text-neutral-700">Design Compile content</p>
          </TabsContent>
          <TabsContent value="sync" className="pt-4">
            <p className="text-sm text-neutral-700">Design Sync content</p>
          </TabsContent>
        </Tabs>
      </RectangleBox>
    </div>
  );
};
export default Design;
