import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

declare const process: {
  env: Record<string, string | undefined>;
};

const backendBaseUrl = process.env.VITE_MUSIO_BACKEND_URL ?? "http://127.0.0.1:18765";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: 18766,
    proxy: {
      "/api": backendBaseUrl
    }
  }
});
