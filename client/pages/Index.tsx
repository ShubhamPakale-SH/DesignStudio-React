import Footer from "@/components/main_components/Footer";

export default function Index() {
  return (
    <div className="min-h-screen flex flex-col bg-background text-foreground">
      <main className="flex-1 flex items-center justify-center">
        <h1 className="text-3xl font-semibold tracking-tight">
          Main Index Page
        </h1>
      </main>
      <Footer />
    </div>
  );
}
