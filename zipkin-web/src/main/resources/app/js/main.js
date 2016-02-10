'use strict';

Array.prototype.remove = function(from, to) {
  var rest = this.slice((to || from) + 1 || this.length);
  this.length = from < 0 ? this.length + from : from;
  return this.push.apply(this, rest);
};

require(
  [
    'flight',
    'jquery'
  ],

  function(flight, $) {
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
      getUrlVar: function(name) {
        return $.getUrlVars()[name];
      }
    });

    var compose = flight.compose;
    var registry = flight.registry;
    var advice = flight.advice;
    var withLogging = flight.logger;
    var debug = flight.debug;

    debug.enable(true);
    compose.mixin(registry, [advice.withAdvice]);

    require(
      [
        './page/default',
        './page/trace',
        './page/dependency'
      ],
      function(
        initializeDefault,
        initializeTrace,
        initializeDependency
      ) {
        initializeDefault();
        initializeTrace();
        initializeDependency();
      }
    );
  }
);
