/* eslint-disable prefer-template */
import {component} from 'flightjs';
import 'chosen-js';
import $ from 'jquery';
import queryString from 'query-string';

export default component(function autocompleteKeys() {
  this.updateValue = function(ev, data) {
    this.renderValue(data.values);
    this.trigger('chosen:updated');
  };

  this.renderValue = function(values) {
    this.$node.empty();
    const selectedValue = queryString.parse(window.location.search).value;
    $.each(values, (i, value) => {
      const option = $($.parseHTML('<option/>'));
      option.val(value);
      option.text(value);
      if (value === selectedValue) {
        option.prop('selected', true);
      }
      this.$node.append(option);
    });
  };

  this.after('initialize', function() {
    this.$node.chosen({search_contains: true});
    this.$node.next('.chosen-container');
    this.on(document, 'updateValue', this.updateValue);
  });
});
