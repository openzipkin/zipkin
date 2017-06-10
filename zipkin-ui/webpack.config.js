/*
 * Copyright 2015-2017 The OpenZipkin Authors
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
var webpack = require('webpack');
var ExtractTextPlugin = require('extract-text-webpack-plugin');
var HtmlWebpackPlugin = require('html-webpack-plugin');
var CopyWebpackPlugin = require('copy-webpack-plugin');

var proxyURL = process.env.proxy || "http://localhost:8080/";
console.log("API requests are forwarded to " + proxyURL);

var webpackConfig = {
    entry: [
        __dirname + '/js/main.js',
        __dirname + '/css/style-loader.js'
    ],
    resolve: {
        modulesDirectories: ['node_modules']
    },
    module: {
        loaders: [{
            test: /\.js$/,
            exclude: /node_modules/,
            loader: 'babel'
        }, {
            test: /\.mustache$/,
            loader: 'mustache'
        }, {
            test: /.scss$/,
            loader: ExtractTextPlugin.extract('style-loader', 'css-loader?sourceMap!sass-loader?sourceMap')
        }, {
            test: /\.woff2?$|\.ttf$|\.eot$|\.svg|\.png$/,
            loader: 'file'
        }]
    },
    output: {
        path: __dirname + '/target/classes/zipkin-ui/',
        filename: 'app-[hash].min.js',
        publicPath: '/'
    },
    devtool: 'source-map',
    plugins: [
        new webpack.ProvidePlugin({
            $: "jquery",
            jQuery: "jquery"
        }),
        new ExtractTextPlugin("app-[hash].min.css", {allChunks: true}),
        new HtmlWebpackPlugin(),
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
