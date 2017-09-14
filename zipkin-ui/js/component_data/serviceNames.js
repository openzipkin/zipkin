import {component} from 'flightjs';
import {getError} from '../../js/component_ui/error';
import $ from 'jquery';
import {contextRoot} from '../publicPath';

export default component(function serviceNames() {
  this.updateServiceNames = function(ev, lastServiceName) {
    $.ajax(`${contextRoot}api/v1/services`, {
      type: 'GET',
      dataType: 'json'
    }).done(names => {
      this.trigger('dataServiceNames', {names, lastServiceName});
    }).fail(e => {
      this.trigger('uiServerError', getError('cannot load service names', e));
    });
  };

  this.after('initialize', function() {
    this.on('uiChangeServiceName', this.updateServiceNames);
  });
});
