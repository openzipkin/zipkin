var webpack = require('webpack');

module.exports = function(config) {
  config.set({
    frameworks: ['mocha', 'chai'],
    files: [
      'test/*test.js',
      'test/**/*test.js'
    ],

    preprocessors: {
      '*test.js': ['webpack', 'sourcemap'],
      '**/*test.js': ['webpack', 'sourcemap']
    },

    client: {
      captureConsole: true
    },

    browsers: ['PhantomJS'],

    webpack: {
      devtool: 'inline-source-map',
      module: {
        loaders: [{
          test: /\.js$/,
          exclude: /node_modules/,
          loader: 'babel'
        }, {
          test: /\.mustache$/,
          loader: 'mustache'
        }]
      },
      resolve: {
        modulesDirectories: ['node_modules']
      },
      plugins: [
        new webpack.ProvidePlugin({
          $: "jquery",
          jQuery: "jquery"
        })
      ]
    },

    webpackServer: {
      noInfo: true
    },

    plugins: [
      require('karma-webpack'),
      require('karma-mocha'),
      require('karma-chai'),
      require('karma-phantomjs-launcher'),
      require('karma-sourcemap-loader')
    ],

    phantomjsLauncher: {
      exitOnResourceError: true
    }
  });
};
