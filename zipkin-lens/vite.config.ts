/// <reference types="vitest" />

import {defineConfig} from 'vite'
import * as path from "path";
// https://vitejs.dev/config/
export default defineConfig({
  server: {
    port: 3000
  },
  resolve: {
    alias: {
      src: path.resolve('src/'),
    },
  },
  test: {
    environment: 'happy-dom'
  },
  base:"/zipkin/",
  build:{
    outDir: 'build'
  },
})
