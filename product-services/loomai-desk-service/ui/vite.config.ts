import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: 5188,
    proxy: {
      "/api": {
        target: process.env.DESK_API_PROXY ?? "http://127.0.0.1:8085",
        changeOrigin: true,
      },
      "/oauth2": {
        target: process.env.DESK_API_PROXY ?? "http://127.0.0.1:8085",
        changeOrigin: true,
      },
      "/auth": {
        target: process.env.DESK_API_PROXY ?? "http://127.0.0.1:8085",
        changeOrigin: true,
      },
      "/logout": {
        target: process.env.DESK_API_PROXY ?? "http://127.0.0.1:8085",
        changeOrigin: true,
      },
    },
  },
  build: { outDir: "dist", sourcemap: false },
});
