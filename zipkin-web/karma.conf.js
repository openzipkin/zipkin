var webpack = require('webpack');

module.exports = function(config) {
  config.set({
    frameworks: ['mocha', 'chai'],
    files: [
      'src/main/resources/app/test/*test.js',
      'src/main/resources/app/test/**/*test.js'
    ],

    preprocessors: {
      '*test.js': ['webpack'],
      '**/*test.js': ['webpack']
    },

    client: {
      captureConsole: true
    },

    browsers: ['PhantomJS'],

    webpack: {
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
        alias: { flight: 'flightjs', chosen: 'chosen-npm/public/chosen.jquery.js' },
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
      require('karma-phantomjs-launcher')
    ],

    phantomjsLauncher: {
      exitOnResourceError: true
    }
  });
};
