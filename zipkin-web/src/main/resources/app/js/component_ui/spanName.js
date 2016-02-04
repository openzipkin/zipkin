'use strict';

define(
  [
    'flight',
    'chosen'
  ],

  function (flight, chosen) {
    return flight.component(spanName);

    function spanName() {
      this.updateSpans = function(ev, data) {
        var html =
          "<option value='all'>all</option>" +
          $.map(data.spans, function(span) {
            return "<option value='"+span+"'>"+span+"</option>";
          }).join("");
        this.$node.html(html);
        this.trigger('chosen:updated');
      };

      this.after('initialize', function() {
        this.$node.chosen(
        {
          search_contains: true
        });
        this.on(document, 'dataSpanNames', this.updateSpans);
      });
    }

  }
);
