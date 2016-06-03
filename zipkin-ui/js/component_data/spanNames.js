import {component} from 'flightjs';
import {getError} from '../../js/component_ui/error';
import $ from 'jquery';

export default component(function spanNames() {
  this.updateSpanNames = function(ev, serviceName) {
    $.ajax(`/api/v1/spans?serviceName=${serviceName}`, {
      type: 'GET',
      dataType: 'json'
    }).done(spans => {
      this.trigger('dataSpanNames', {spans});
    }).fail(e => {
      this.trigger('uiServerError', getError('cannot load span names', e));
    });
  };

  this.after('initialize', function() {
    this.on('uiChangeServiceName', this.updateSpanNames);
  });
});
