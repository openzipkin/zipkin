/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
/// <reference types="vitest" />

import {defineConfig, UserConfig} from 'vite'
import * as path from "path";

// baseUrl is the default path to lookup assets.
const baseUrl = process.env.BASE_URL || '/zipkin';

const zipkinProxyConfig = {
  target: process.env.API_BASE || 'http://localhost:9411',
  changeOrigin: true,
};

export default defineConfig(():UserConfig => {
  // https://vitejs.dev/config/
  return {
    server: {
      port: 3000,
      proxy: {
        '/zipkin/api/v2': zipkinProxyConfig,
        '/zipkin/config.json': zipkinProxyConfig,
      },
    },
    resolve: {
      alias: {
        src: path.resolve('src/'),
      },
    },
    // @ts-ignore
    test: {
      environment: 'happy-dom'
    },
    base: baseUrl,
    build: {
      outDir: 'build',
      // use the same path patterns as the original react-scripts lens build
      assetsDir: "static",
      // uncomment to build with sourcemap, for troubleshooting zipkin-server
      // sourcemap: true,
      rollupOptions: {
        output: {
          assetFileNames({name}):string {
            if (name?.includes('.css')) return 'static/css/[name].[hash].css'
            if (name?.includes('.png')) return 'static/media/[name].[hash].png'
            return 'static/[name].[hash][extname]'
          },
          chunkFileNames: `static/js/[name].[hash].js`,
          entryFileNames: `static/js/[name].[hash].js`,
        },
      },
    },
  }
})
