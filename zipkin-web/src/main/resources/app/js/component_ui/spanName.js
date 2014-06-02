'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(spanName);

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
        this.$node.chosen();
        this.on(document, 'dataSpanNames', this.updateSpans);
      });
    }

  }
);
