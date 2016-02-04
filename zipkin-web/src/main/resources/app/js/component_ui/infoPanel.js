'use strict';

define(
  [
    'flight',
    'bootstrap/dist/js/npm'
  ],

  function (flight, bootstrapNpm) {

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
