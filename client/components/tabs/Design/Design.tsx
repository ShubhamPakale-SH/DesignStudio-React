import RectangleBox from "@/components/Reusable Components/RectangleBox";

const Design = () => (
  <div className="w-full h-full flex items-center justify-center p-6">
    <RectangleBox className="max-w-xl w-full text-center">
      <h2 className="text-2xl font-semibold mb-2">Design</h2>
      <p className="text-sm text-neutral-600">
        This content is wrapped in a reusable rectangle box with shadow.
      </p>
    </RectangleBox>
  </div>
);
export default Design;
