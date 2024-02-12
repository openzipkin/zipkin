/*
 * Copyright 2015-2024 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
/// <reference types="vitest" />

import {defineConfig} from 'vite'
import * as path from "path";

// baseUrl is the default path to lookup assets.
const baseUrl = process.env.BASE_URL || '/zipkin';
// basePath is the default path to get dynamic resources from zipkin.
const basePath = process.env.BASE_PATH || baseUrl;

const zipkinProxyConfig = {
  target: process.env.API_BASE || 'http://localhost:9411',
  changeOrigin: true,
};

export default defineConfig(() => {
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
    test: {
      environment: 'happy-dom'
    },
    plugins: [
      {
        name: 'Replace head.base.href with BASE_PATH variable',
        transformIndexHtml: html => html.replace('href="/zipkin"', `href="${basePath}"`)
      },
    ],
    base: baseUrl,
    build: {
      outDir: 'build',
      // use the same path patterns as the original react-scripts lens build
      assetsDir: "static",
      rollupOptions: {
        output: {
          assetFileNames({name}) {
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
