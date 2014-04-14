'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(serviceNameFilter);

    function serviceNameFilter() {
      this.onChange = function(e, params) {
        if (params.selected === "") return;

        this.trigger(document, 'uiAddServiceNameFilter', {value: params.selected});
        this.$node.val('');
        this.$node.trigger('chosen:updated');
      };

      this.after('initialize', function() {
        this.$node.chosen({search_contains: true});
        this.on('change', this.onChange);
      });
    }

  }
);
