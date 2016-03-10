'use strict';

define(
  [
    'flightjs',
    'chosen-npm/public/chosen.jquery.js',
    'query-string'
  ],

  function (flight, chosen, queryString) {
    return flight.component(spanName);

    function spanName() {
      this.updateSpans = function(ev, data) {
        this.render(data.spans);
        this.trigger('chosen:updated');
      };

      this.render = function(spans) {
        const selectedSpanName = queryString.parse(window.location.search).spanName;
        var html =
          "<option value='all'>all</option>" +
          $.map(spans, function(span) {
            const selected = span === selectedSpanName ? 'selected' : '';
            return "<option value='"+span+"' "+selected+">"+span+"</option>";
          }).join("");
        this.$node.html(html);
      };

      this.after('initialize', function() {
        this.$node.chosen({
          search_contains: true
        });
        this.on(document, 'dataSpanNames', this.updateSpans);
      });
    }

  }
);
