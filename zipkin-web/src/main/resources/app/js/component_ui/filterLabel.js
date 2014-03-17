'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(filterLabel);

    function filterLabel() {
      this.serviceName = "";

      this.addFilter = function(e) {
        this.trigger('uiAddServiceNameFilter', {value: this.serviceName});
      };

      this.filterAdded = function(e, data) {
        if (data.value === this.serviceName)
          this.$node.addClass('service-tag-filtered');
      };

      this.filterRemoved = function(e, data) {
        if (data.value === this.serviceName)
          this.$node.removeClass('service-tag-filtered');
      };

      this.after('initialize', function() {
        this.serviceName = this.$node.attr('data-serviceName');
        this.on('click', this.addFilter);
        this.on(document, 'uiAddServiceNameFilter', this.filterAdded);
        this.on(document, 'uiRemoveServiceNameFilter', this.filterRemoved);
      });
    }
  }
);
