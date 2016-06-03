/* eslint-disable prefer-template */
import {component} from 'flightjs';
import chosen from 'chosen-npm/public/chosen.jquery.js'; // eslint-disable-line no-unused-vars
import $ from 'jquery';
import queryString from 'query-string';

export default component(function spanName() {
  this.updateSpans = function(ev, data) {
    this.render(data.spans);
    this.trigger('chosen:updated');
  };

  this.render = function(spans) {
    const selectedSpanName = queryString.parse(window.location.search).spanName;
    const html =
      '<option value="all">all</option>' +
      $.map(spans, span => {
        const selected = span === selectedSpanName ? 'selected' : '';
        return `<option value='${span}' ${selected}>${span}</option>`;
      }).join('');
    this.$node.html(html);
  };

  this.after('initialize', function() {
    this.$node.chosen({
      search_contains: true
    });
    this.on(document, 'dataSpanNames', this.updateSpans);
  });
});
