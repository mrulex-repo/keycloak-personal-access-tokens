import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: "dist",
    lib: {
      entry: "src/account/main.tsx",
      formats: ["es"],
    },
    rollupOptions: {
      external: ["react", "react-dom", "react-router-dom"],
    },
  },
});
