import path from "node:path";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import type { Plugin } from "vite";
import { defineConfig } from "vitest/config";

/** Starts the headline font downloading with the HTML, because the largest text on the page waits for it. */
function preloadHeadlineFont(): Plugin {
  return {
    name: "preload-headline-font",
    enforce: "post",
    transformIndexHtml(html, context) {
      const font = Object.keys(context.bundle ?? {}).find((name) => /anek-latin-latin-wdth.*\.woff2$/.test(name));
      if (!font) return html;
      return [{ tag: "link", attrs: { rel: "preload", as: "font", type: "font/woff2", href: `/${font}`, crossorigin: "" }, injectTo: "head" }];
    },
  };
}

export default defineConfig({
  plugins: [react(), tailwindcss(), preloadHeadlineFont()],
  resolve: { alias: { "@": path.resolve(import.meta.dirname, "./src") } },
  test: {
    environment: "jsdom",
    globals: true,
    include: ["src/**/*.test.{ts,tsx}"],
    setupFiles: ["./src/test-setup.ts"],
  },
});
