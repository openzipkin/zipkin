'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(goToTrace);

    function goToTrace() {
      this.navigateToTrace = function(evt) {
        evt.preventDefault()
        var traceId = document.getElementById('traceIdQuery').value
        window.location.href='/traces/' + traceId
      };

      this.after('initialize', function() {
        this.on('submit', this.navigateToTrace);
      });
    }

  }
);
