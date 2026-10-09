import { defineConfig } from "vite";
import path from "path";

export default defineConfig({
  resolve: {
    alias: { "@": path.resolve(__dirname, "./src") },
  },
  build: {
    lib: {
      entry: path.resolve(__dirname, "src/installer/install.ts"),
      name: "LoomAIWorkspaceInstaller",
      formats: ["iife"],
      fileName: () => "ai-workspace-installer.iife.js",
    },
    rollupOptions: { output: { inlineDynamicImports: true } },
    outDir: "dist",
    emptyOutDir: false,
    sourcemap: false,
    minify: "terser",
    terserOptions: { compress: { drop_console: true, passes: 2 } },
  },
});
