'use strict';

Array.prototype.remove = function(from, to) {
  var rest = this.slice((to || from) + 1 || this.length);
  this.length = from < 0 ? this.length + from : from;
  return this.push.apply(this, rest);
};

$.extend({
  getUrlVars: function(){
    var vars = [], hash;
    var hashes = window.location.href.slice(window.location.href.indexOf('?') + 1).split('&');
    for(var i = 0; i < hashes.length; i++)
    {
      hash = hashes[i].split('=');
      vars.push(hash[0]);
      vars[hash[0]] = hash[1];
    }
    return vars;
  },
  getUrlVar: function(name){
    return $.getUrlVars()[name];
  }
});

requirejs.config({
  baseUrl: '/app/libs',
  paths: {
    'component_data': '../js/component_data',
    'component_ui': '../js/component_ui',
    'page': '../js/page',
    'dagre-d3': 'dagre-d3/js/dagre-d3'
  },
  shim: {
    'dagre-d3': {
      deps: ['d3/d3'],
      exports: 'dagreD3'
    },
    'd3/d3': {
      exports: 'd3'
    }
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

    require(
      [
        'page/default',
        'page/trace',
        'page/aggregate'
      ],
      function(
        initializeDefault,
        initializeTrace,
        initializeAggregate
      ) {
        initializeDefault();
        initializeTrace();
        initializeAggregate();
      }
    );
  }
);
