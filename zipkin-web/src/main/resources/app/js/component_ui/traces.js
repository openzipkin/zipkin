'use strict';

define(
  [
    'flight/lib/component',
    'component_ui/filterLabel'
  ],

  function (
    defineComponent,
    FilterLabelUI
  ) {
    FilterLabelUI.attachTo('.service-filter-label');

    return defineComponent(traces);

    function traces() {
      this.$traces = [];
      this.services = [];

      this.triggerUpdateTraces = function() {
        this.$node.trigger('uiUpdateTraces', {traces: this.$traces.filter(":visible")});
      };

      this.updateTraces = function() {
        var services = this.services;
        this.$traces.each(function() {
          var $trace = $(this);
          if (services.length > 0) {
            var show = true;
            $.each(services, function(idx, svc) {
              if (!$trace.has(".service-filter-label[data-service-name='" + svc + "']").length)
                show = false
            });

            $trace[show ? 'show' : 'hide']();
          } else {
            $trace.show()
          }
        });

        this.triggerUpdateTraces();
      };

      this.addFilter = function(ev, data) {
        if ($.inArray(data.value, this.services) == -1) {
          this.services.push(data.value);
          this.updateTraces();
        }
      };

      this.removeFilter = function(ev, data) {
        var idx = $.inArray(data.value, this.services);
        if (idx > -1) {
          this.services.splice(idx, 1);
          this.updateTraces();
        }
      };

      this.sortFunctions = {
        'service-percentage-desc': function(a ,b) { return b.percentage - a.percentage; },
        'service-percentage-asc': function(a ,b) { return a.percentage - b.percentage; },
        'duration-desc': function(a, b) { return b.duration - a.duration; },
        'duration-asc': function(a, b) { return a.duration - b.duration; },
        'timestamp-desc': function(a, b) { return b.timestamp - a.timestamp; },
        'timestamp-asc': function(a, b) { return a.timestamp - b.timestamp; }
      };

      this.updateSortOrder = function(ev, data) {
        if (this.sortFunctions.hasOwnProperty(data.order)) {
          this.$traces.sort(this.sortFunctions[data.order]);

          this.$node.html(this.$traces);

          // Flight needs something like jQuery's $.live() functionality
          FilterLabelUI.teardownAll();
          FilterLabelUI.attachTo('.service-filter-label');

          this.triggerUpdateTraces();
        }
      };

      this.after('initialize', function() {
        this.$traces = this.$node.find('.trace');
        this.$traces.each(function() {
          var $this = $(this);
          this.duration = parseInt($this.data('duration'), 10),
          this.timestamp = parseInt($this.data('timestamp'), 10)
          this.percentage = parseInt($this.data('servicePercentage'), 10);
        });
        this.on(document, 'uiAddServiceNameFilter', this.addFilter);
        this.on(document, 'uiRemoveServiceNameFilter', this.removeFilter);
        this.on(document, 'uiUpdateTraceSortOrder', this.updateSortOrder);
      });
    }
  }
);
