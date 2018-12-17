import {component} from 'flightjs';
import {getError} from '../component_ui/error';
import $ from 'jquery';

export default component(
  function autocompleteKeysAndValues() {
    this.autocompleteKeys = function(ev, lastTag) {
      $.ajax('api/v2/autocompleteKeys', {
        type: 'GET',
        dataType: 'json'
      }).done(keys => {
        this.trigger('updateKey', {keys: keys.sort(), lastTag});
      }).fail(e => {
        this.trigger('uiServerError', getError('cannot load service names', e));
      });
    };

    this.autocompleteValues = function(ev, autocompleteKey) {
      $.ajax(`api/v2/autocompleteValues?key=${autocompleteKey}`, {
        type: 'GET',
        dataType: 'json'
      }).done(values => {
        this.trigger('updateValue', {values: values.sort()});
      }).fail(e => {
        this.trigger('uiServerError', getError('cannot load service names', e));
      });
    };

    this.after('initialize', function() {
      this.on('uiautocompletekeys', this.autocompleteKeys);
      this.on('uiautocompletevalues', this.autocompleteValues);
    });
  });
