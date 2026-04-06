import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";
import cssInjectedByJsPlugin from "vite-plugin-css-injected-by-js";
import path from "path";

// IIFE build: self-contained bundle with React included
// Used via <script> tag — no dependencies needed
export default defineConfig({
  plugins: [
    react(),
    cssInjectedByJsPlugin({
      // Inject CSS into Shadow DOM instead of document.head
      styleId: "max-mode-widget-styles",
    }),
  ],
  define: {
    "process.env.NODE_ENV": JSON.stringify("production"),
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  build: {
    lib: {
      entry: path.resolve(__dirname, "src/entries/iife.ts"),
      name: "MaxMode",
      formats: ["iife"],
      fileName: () => "max-mode-widget.iife.js",
    },
    // DO NOT externalize react — bundle it inside for script-tag users
    rollupOptions: {
      output: {
        // Ensure everything is in one file
        inlineDynamicImports: true,
      },
    },
    cssCodeSplit: false,
    sourcemap: false,
    minify: "terser",
    terserOptions: {
      compress: {
        drop_console: true,
        passes: 2,
      },
    },
    outDir: "dist",
    emptyOutDir: false, // Don't wipe ESM build output
  },
});
