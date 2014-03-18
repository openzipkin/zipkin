'use strict';

requirejs.config({
  baseUrl: 'app/libs',
  paths: {
    'component_data': '../js/component_data',
    'component_ui': '../js/component_ui',
    'page': '../js/page'
  }
});

require(
  [
    'flight/lib/compose',
    'flight/lib/registry',
    'flight/lib/advice',
    'flight/lib/logger',
    'flight/lib/debug'
  ],

  function(compose, registry, advice, withLogging, debug) {
    debug.enable(true);
    compose.mixin(registry, [advice.withAdvice]);

    require(['page/default'], function(initializeDefault) {
      initializeDefault();
    });
  }
);
