/* eslint-disable prefer-template */
import {component} from 'flightjs';
import 'chosen-js';
import $ from 'jquery';
import queryString from 'query-string';

export default component(function spanName() {
  this.updateSpans = function(ev, data) {
    this.render(data.spans);
    this.trigger('chosen:updated');
  };

  this.render = function(spans) {
    this.$node.empty();
    this.$node.append($($.parseHTML('<option value="all">all</option>')));

    const selectedSpanName = queryString.parse(window.location.search).spanName;
    $.each(spans, (i, span) => {
      const option = $($.parseHTML('<option/>'));
      option.val(span);
      option.text(span);
      if (span === selectedSpanName) {
        option.prop('selected', true);
      }
      this.$node.append(option);
    });
  };

  this.after('initialize', function() {
    this.$node.chosen({
      search_contains: true
    });
    this.$node.next('.chosen-container');

    this.on(document, 'dataSpanNames', this.updateSpans);
  });
});
