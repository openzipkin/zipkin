import {component} from 'flightjs';
import $ from 'jquery';

export default component(function traces() {
  this.$traces = [];

  this.sortFunctions = {
    'service-percentage-desc': (a, b) => b.percentage - a.percentage,
    'service-percentage-asc': (a, b) => a.percentage - b.percentage,
    'duration-desc': (a, b) => b.duration - a.duration,
    'duration-asc': (a, b) => a.duration - b.duration,
    'timestamp-desc': (a, b) => b.timestamp - a.timestamp,
    'timestamp-asc': (a, b) => a.timestamp - b.timestamp
  };

  this.updateSortOrder = function(ev, data) {
    if (this.sortFunctions.hasOwnProperty(data.order)) {
      this.$traces.sort(this.sortFunctions[data.order]);
      this.$node.html(this.$traces);
      this.triggerUpdateTraces();
    }
  };

  this.after('initialize', function() {
    this.$traces = this.$node.find('.trace');
    this.$traces.each(function() {
      const $this = $(this);
      this.duration = parseInt($this.data('duration'), 10);
      this.timestamp = parseInt($this.data('timestamp'), 10);
      this.percentage = parseInt($this.data('servicePercentage'), 10);
    });
    this.on(document, 'uiAddServiceNameFilter', this.addFilter);
    this.on(document, 'uiRemoveServiceNameFilter', this.removeFilter);
    this.on(document, 'uiUpdateTraceSortOrder', this.updateSortOrder);
    const sortOrderSelect = $('.sort-order');
    this.updateSortOrder(null, {order: sortOrderSelect.val()});
  });
});
