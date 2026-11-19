import path from "node:path";
import { defineConfig } from "vite";

const exampleRoot = import.meta.dirname;

export default defineConfig({
  root: "./src",
  build: {
    outDir: "../dist",
    minify: false,
    emptyOutDir: true,
    rollupOptions: {
      input: {
        main: path.resolve(exampleRoot, "src/index.html"),
        visualRegressionProbe: path.resolve(exampleRoot, "src/visual-regression-probe.html"),
      },
    },
  },
});
