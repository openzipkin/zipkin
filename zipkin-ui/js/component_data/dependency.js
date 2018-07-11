import {component} from 'flightjs';
import moment from 'moment';
import $ from 'jquery';
import {traceSummary, traceSummariesToMustache} from '../component_ui/traceSummary';

export default component(function dependency() {
  let services = {};
  let dependencies = {};

  this.getDependency = function(endTs, lookback) {
    let url = `api/v2/dependencies?endTs=${endTs}`;
    if (lookback) {
      url += `&lookback=${lookback}`;
    }
    $.ajax(url, {
      type: 'GET',
      dataType: 'json',
      success: links => {
        this.links = links;
        this.buildServiceData(links);
        this.trigger('dependencyDataReceived', links);
      },
      failure: (jqXHR, status, err) => {
        const error = {
          message: `Couldn't get dependency data from backend: ${err}`
        };
        this.trigger('dependencyDataFailed', error);
      }
    });
  };

  this.filterDependency = function(parent, child, endTs, lookback, limit, error, serviceName) {
    const apiURL = `api/v2/traces?parentService=${parent}&childService=${child}&lookback=
    ${lookback}&endTs=${endTs}&limit=${limit}&error=${error}`;
    $.ajax(apiURL, {
      type: 'GET',
      dataType: 'json'
    }).done(traces => {
      const traceView = {
        traces: traceSummariesToMustache('all', traces.map(traceSummary)),
        apiURL,
        rawResponse: traces,
        serviceName
      };
      this.trigger('filterLinkDataRecieved', traceView);
    }).fail(e => {
      this.trigger('defaultPageModelView', {traces: 'No traces to show', error: e});
    });
  };

  this.buildServiceData = function(links) {
    services = {};
    dependencies = {};
    links.forEach(link => {
      const {parent, child} = link;
      dependencies[parent] = dependencies[parent] || {};
      dependencies[parent][child] = link;
      services[parent] = services[parent] || {serviceName: parent, uses: [], usedBy: []};
      services[child] = services[child] || {serviceName: child, uses: [], usedBy: []};
      services[parent].uses.push(child);
      services[child].usedBy.push(parent);
    });
  };

  this.after('initialize', function() {
    this.on(document, 'dependencyDataRequested', function(event, {endTs, lookback}) {
      this.getDependency(endTs, lookback);
    });

    this.on(document, 'serviceDataRequested', function(event, {serviceName}) {
      this.getServiceData(serviceName, data => {
        this.trigger(document, 'serviceDataReceived', data);
      });
    });

    this.on(document, 'parentChildDataRequested', function(event, {parent, child}) {
      this.getDependencyData(parent, child, data => {
        this.trigger(document, 'parentChildDataReceived', data);
      });
    });
    const endTs = document.getElementById('endTs').value || moment().valueOf();
    const startTs = document.getElementById('startTs').value;
    let lookback;
    if (startTs && endTs > startTs) {
      lookback = endTs - startTs;
    }
    this.getDependency(endTs, lookback);
    this.on(document, 'filterLinkDataRequested',
    function(event, {parentService, childService, limit, error, serviceName}) {
      this.filterDependency(parentService, childService, endTs, lookback, limit, error, serviceName);
    });
  });

  this.getServiceData = function(serviceName, callback) {
    callback(services[serviceName]);
  };

  this.getDependencyData = function(parent, child, callback) {
    callback(dependencies[parent][child]);
  };
});
