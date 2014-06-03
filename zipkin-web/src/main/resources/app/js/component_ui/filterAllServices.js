'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(filterAllServices);

    function filterAllServices() {
      this.$expandAll = $();
      this.$collapseAll = $();
      this.totalServices = 0;
      this.filtered = {};
      this.currentFilterCount = 0;

      this.toggleFilter = function(e) {
        this.trigger(document, $(e.target).val());
      };

      this.filterAdded = function(e, data) {
        if (this.filtered[data.value]) return;

        this.filtered[data.value] = true;
        this.currentFilterCount += 1;

        if (this.currentFilterCount === this.totalServices)
          this.$expandAll.addClass('active');
        else
          this.$collapseAll.removeClass('active');
      };

      this.filterRemoved = function(e, data) {
        if (!this.filtered[data.value]) return;

        this.filtered[data.value] = false;
        this.currentFilterCount -= 1;

        if (this.currentFilterCount === 0)
          this.$collapseAll.addClass('active');
        else
          this.$expandAll.removeClass('active');
      };

      this.after('initialize', function(node, data) {
        this.totalServices = data.totalServices;
        this.$expandAll = this.$node.find('[value="uiExpandAllSpans"]');
        this.$collapseAll = this.$node.find('[value="uiCollapseAllSpans"]');

        this.on('.btn', 'click', this.toggleFilter);
        this.on(document, 'uiAddServiceNameFilter', this.filterAdded);
        this.on(document, 'uiRemoveServiceNameFilter', this.filterRemoved);
      });
    }
  }
);
