// We use this file to let webpack generate
// a css bundle from our stylesheets.

// read the public path from the <base> tag where it has to be set anyway because of
// html-webpack-plugin limitations: https://github.com/jantimon/html-webpack-plugin/issues/119
// otherwise it could be: window.location.pathname.replace(/(.*)\/zipkin\/.*/, '$1/zipkin/')
__webpack_public_path__ = $('base').attr('href'); // eslint-disable-line camelcase, no-undef

require('./main.scss');
