/* eslint-disable prefer-template */
import {component} from 'flightjs';
import 'chosen-js';
import $ from 'jquery';
import queryString from 'query-string';
import Cookies from 'js-cookie';

export default component(function autocompleteKeys() {
  this.onChange = function() {
    Cookies.set('last-tagkey', this.$node.val());
    this.$node.trigger('uiautocompletevalues', this.$node.val());
  };

  this.updateKey = function(ev, data) {
    this.renderKey(data);
    this.trigger('chosen:updated');
  };

  this.renderKey = function(data) {
    this.$node.empty();
    const selectedKey = queryString.parse(window.location.search).key;
    $.each(data.keys, (i, key) => {
      const option = $($.parseHTML('<option/>'));
      option.val(key);
      option.text(key);
      if (key === selectedKey) {
        option.prop('selected', true);
      }
      this.$node.append(option);
    });
    // There won't be any tag keys at the first load, load the first one and its 
    // values
    if (!data.lastTag && data.keys && data.keys.length > 1) {
      this.$node.trigger('uiautocompletevalues', data.keys[0]);
    }
  };
  this.after('initialize', function() {
    this.$node.chosen({search_contains: true});
    this.$node.trigger('uiautocompletekeys');
    this.$node.next('.chosen-container');
    this.on('change', this.onChange);
    this.on(document, 'updateKey', this.updateKey);
  });
});
