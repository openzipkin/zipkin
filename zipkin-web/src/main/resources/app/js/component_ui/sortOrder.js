'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(sortOrder);

    function sortOrder() {
      this.updateSortOrder = function(ev, data) {
        this.$node.val(data.order);
      };
      this.after('initialize', function () {
        this.on(document, 'uiUpdateTraceSortOrder', this.updateSortOrder);
      });
    }
  }
);