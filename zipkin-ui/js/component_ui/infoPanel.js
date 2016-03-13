'use strict';

define(
  [
    'flightjs',
    'bootstrap-sass/assets/javascripts/bootstrap.js'
  ],

  function (flight, bootstrap) {

    return flight.component(infoPanel);

    function infoPanel() {
      this.show = function() {
        this.$node.modal('show');
      };

      this.after('initialize', function() {
        this.$node.modal('hide');
        this.on(document, 'uiRequestInfoPanel', this.show);
      });
    }

  }
);
