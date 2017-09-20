import $ from 'jquery';

// read the public path from the <base> tag where it has to be set anyway because of
// html-webpack-plugin limitations: https://github.com/jantimon/html-webpack-plugin/issues/119
// otherwise it could be: window.location.pathname.replace(/(.*)\/zipkin\/.*/, '$1/zipkin/')
let contextRoot = $('base').attr('href') || '/zipkin/';
contextRoot += contextRoot.endsWith('/') ? '' : '/';

// set dynamically 'output.publicPath' as per https://webpack.github.io/docs/configuration.html#output-publicpath
__webpack_public_path__ = contextRoot; // eslint-disable-line camelcase, no-undef

export {contextRoot};
