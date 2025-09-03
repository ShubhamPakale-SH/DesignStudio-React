import Header from "@/components/main_components/Header";
import Sidebar from "@/components/main_components/Sidebar";
import Footer from "@/components/main_components/Footer";

export default function Index() {
  return (
    
      <div className="flex flex-col min-h-0">
      <div className="flex flex-1 min-h-0">
        <Sidebar />
        <div className="min-h-screen flex flex-col bg-background text-foreground">
          <Header />
        </div>
        <main className="flex-1 flex items-center justify-center shadow-[1px_1px_3px_0_rgba(0,0,0,1)]">
          <h1 className="text-[30px] leading-9 font-semibold tracking-[-0.75px]">
            Main Index Page
          </h1>
        </main>
      </div>
      <Footer />
    </div>
    
  );
}
