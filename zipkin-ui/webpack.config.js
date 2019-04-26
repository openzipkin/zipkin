/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
var webpack = require('webpack');
var ExtractTextPlugin = require('extract-text-webpack-plugin');
var HtmlWebpackPlugin = require('html-webpack-plugin');
var CopyWebpackPlugin = require('copy-webpack-plugin');

var isDevServer = process.argv.find(v => v.includes('webpack-dev-server'));
var proxyURL = process.env.proxy || "http://localhost:8080/";
console.log("API requests are forwarded to " + proxyURL);

var webpackConfig = {
    entry: [
        __dirname + '/js/main.js',
        __dirname + '/css/style-loader.js'
    ],
    resolve: {
        modules: ['node_modules']
    },
    module: {
        rules: [{
            test: /\.js$/,
            exclude: /node_modules/,
            use: 'babel-loader'
        }, {
            test: /\.mustache$/,
            use: 'mustache-loader'
        }, {
            test: /.scss$/,
            use: ExtractTextPlugin.extract({
              fallback: 'style-loader',
              use: 'css-loader?sourceMap!sass-loader?sourceMap'
            })
        }, {
            test: /\.woff2?$|\.ttf$|\.eot$|\.svg|\.png$/,
            use: 'file-loader'
        }]
    },
    output: {
        path: __dirname + '/target/classes/zipkin-ui/',
        filename: 'app-[hash].min.js'
        // 'publicPath' must not be set here in order to support Zipkin running in any context root.
        // '__webpack_public_path__' has to be set dynamically (see './publicPath.js' module file) as per
        // https://webpack.github.io/docs/configuration.html#output-publicpath
    },
    devtool: 'source-map',
    plugins: [
        new webpack.ProvidePlugin({
            $: "jquery",
            jQuery: "jquery"
        }),
        new ExtractTextPlugin("app-[hash].min.css", {allChunks: true}),
        new HtmlWebpackPlugin({
          template: __dirname + '/index.ejs',
          contextRoot: isDevServer ? '/' : '/zipkin/'
        }),
        new CopyWebpackPlugin([
            { from: 'static' }
        ])
    ],
    devServer: {
        historyApiFallback: true,
        port: 9090,
        proxy: {
            "/api/*": proxyURL,
            "/config.json": proxyURL
        }
    }
};

module.exports = webpackConfig;
