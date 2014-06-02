'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(infoButton);

    function infoButton() {
      this.requestInfoPanel = function() {
        this.trigger('uiRequestInfoPanel');
      };

      this.after('initialize', function() {
        this.on('click', this.requestInfoPanel);
      });
    }

  }
);
