'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(infoPanel);

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
