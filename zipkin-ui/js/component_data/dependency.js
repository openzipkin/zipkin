import {component} from 'flightjs';
import moment from 'moment';
import $ from 'jquery';

export default component(function dependency() {
  var links = [];
  var services = {};
  var dependencies = {};

  this.getDependency = function (endTs, lookback) {
    var url = "/api/v1/dependencies?endTs=" + endTs;
    if (lookback) url += "&lookback=" + lookback;
    $.ajax(url, {
      type: "GET",
      dataType: "json",
      success: links => {
        this.links = links;
        this.buildServiceData(links);
        this.trigger('dependencyDataReceived', links);
      },
      failure: (jqXHR, status, err) => {
        var error = {
          message: "Couldn't get dependency data from backend: " + err
        };
        this.trigger('dependencyDataFailed', error);
      }
    });
  };

  this.buildServiceData = function (links) {
    services = {};
    dependencies = {};
    links.forEach(function (link) {
      var parent = link.parent;
      var child = link.child;

      dependencies[parent] = dependencies[parent] || {};
      dependencies[parent][child] = link;

      services[parent] = services[parent] || {serviceName: parent, uses: [], usedBy: []};
      services[child] = services[child] || {serviceName: child, uses: [], usedBy: []};

      services[parent].uses.push(child);
      services[child].usedBy.push(parent);
    });
  };

  this.after('initialize', function () {
    this.on(document, 'dependencyDataRequested', function (event, {endTs, lookback}) {
      this.getDependency(endTs, lookback);
    });

    this.on(document, 'serviceDataRequested', function (event, {serviceName}) {
      this.getServiceData(serviceName, function (data) {
        this.trigger(document, 'serviceDataReceived', data);
      }.bind(this));
    });

    this.on(document, 'parentChildDataRequested', function (event, {parent, child}) {
      this.getDependencyData(parent, child, function (data) {
        this.trigger(document, 'parentChildDataReceived', data);
      }.bind(this));
    });

    var endTs = document.getElementById('endTs').value || moment().valueOf();
    var startTs = document.getElementById('startTs').value;
    var lookback;
    if (startTs && endTs > startTs) {
      lookback = endTs - startTs;
    }
    this.getDependency(endTs, lookback);
  });

  this.getServiceData = function (serviceName, callback) {
    callback(services[serviceName]);
  };

  this.getDependencyData = function (parent, child, callback) {
    callback(dependencies[parent][child]);
  };
});
