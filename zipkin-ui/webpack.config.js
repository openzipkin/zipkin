var webpack = require('webpack');
var ExtractTextPlugin = require('extract-text-webpack-plugin');

module.exports = {
    entry: [
        __dirname + '/js/main.js',
        __dirname + '/css/style-loader.js',
        __dirname + '/static/static-loader.js'
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
        path: __dirname + '/dist/static',
        filename: 'app.min.js',
        publicPath: '/'
    },
    devtool: 'source-map',
    plugins: [
        new webpack.ProvidePlugin({
            $: "jquery",
            jQuery: "jquery"
        }),
        new ExtractTextPlugin("app.min.css", {allChunks: true})
    ],
    devServer: {
        port: 9090,
        proxy: {
            "*": "http://localhost:8080"
        }
    }
};
