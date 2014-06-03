'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(fullPageSpinner);

    function fullPageSpinner() {
      this.requests = 0;

      this.showSpinner = function() {
        this.requests += 1;
        this.$node.show();
      };

      this.hideSpinner = function() {
        this.requests -= 1;
        if (this.requests == 0)
          this.$node.hide();
      };

      this.after('initialize', function() {
        this.on(document, 'uiShowFullPageSpinner', this.showSpinner);
        this.on(document, 'uiHideFullPageSpinner', this.hideSpinner);
      });
    }
  }
);
