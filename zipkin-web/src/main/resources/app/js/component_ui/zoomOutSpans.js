'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(zoomOutSpans);

    function zoomOutSpans() {
      this.zoomOut = function() {
        this.trigger('uiZoomOutSpans');
      };

      this.after('initialize', function() {
        this.on('click', this.zoomOut);
      });
    }

  }
);

