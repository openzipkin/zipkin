import {component} from 'flightjs';
import {getError} from '../../js/component_ui/error';
import $ from 'jquery';

export default component(function remoteServiceNames() {
  this.updateRemoteServiceNames = function(ev, serviceName) {
    if (!serviceName) {
      this.trigger('dataRemoteServiceNames', {remoteServices: []});
      return;
    }
    $.ajax(`api/v2/remoteServices?serviceName=${serviceName}`, {
      type: 'GET',
      dataType: 'json'
    }).done(remoteServices => {
      this.trigger('dataRemoteServiceNames', {remoteServices: remoteServices.sort()});
    }).fail(e => {
      if (e.status && e.status === 404) { // remote service names is a new endpoint
        this.trigger('dataRemoteServiceNames', {remoteServices: []});
        return;
      }
      this.trigger('uiServerError', getError('cannot load remote service names', e));
    });
  };

  this.after('initialize', function() {
    this.on('uiChangeServiceName', this.updateRemoteServiceNames);
    this.on('uiFirstLoadRemoteServiceNames', this.updateRemoteServiceNames);
  });
});
