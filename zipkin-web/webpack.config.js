var webpack = require('webpack');

module.exports = {
    entry: __dirname + '/src/main/resources/app/js/main.js',
    resolve: {
        alias: { flight: 'flightjs', chosen: 'chosen-npm/public/chosen.jquery.js' }
    },
    module: {
        loaders: [{
            test: /\.js$/,
            exclude: /node_modules/,
            loader: 'babel?presets[]=es2015'
        }, {
            test: /\.mustache$/,
            loader: 'mustache?minify'
        }]
    },
    output: {
        path: __dirname + '/src/main/resources/dist',
        filename: 'app.min.js',
        publicPath: '/dist/'
    },
    plugins: [
        new webpack.ProvidePlugin({
            $: "jquery",
            jQuery: "jquery"
        })
    ],
    devServer: {
        port: 9090,
        proxy: {
            "*": "http://localhost:8080"
        }
    }
};
