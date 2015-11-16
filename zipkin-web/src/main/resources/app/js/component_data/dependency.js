'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(dependency);

    function dependency() {
      var links = [];
      var services = {};
      var dependencies = {};

      this.getDependency = function (endTs) {
        var url = "/api/dependencies?endTs=" + endTs;
        $.ajax(url, {
          type: "GET",
          dataType: "json",
          context: this,
          success: function (links) {
            this.links = links;
            this.buildServiceData(links);
            this.trigger('dependencyDataReceived', links);
          },
          failure: function (jqXHR, status, err) {
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
        this.on(document, 'dependencyDataRequested', function (args) {
          this.getDependency(args.from, args.to)
        });

        this.on(document, 'serviceDataRequested', function (event, args) {
          this.getServiceData(args.serviceName, function (data) {
            this.trigger(document, 'serviceDataReceived', data);
          }.bind(this));
        });

        this.on(document, 'parentChildDataRequested', function (event, args) {
          this.getDependencyData(args.parent, args.child, function (data) {
            this.trigger(document, 'parentChildDataReceived', data);
          }.bind(this));
        });

        this.getDependency(Date.now());
      });

      this.getServiceData = function (serviceName, callback) {
        callback(services[serviceName]);
      };

      this.getDependencyData = function (parent, child, callback) {
        callback(dependencies[parent][child]);
      };
    }
  }
);
