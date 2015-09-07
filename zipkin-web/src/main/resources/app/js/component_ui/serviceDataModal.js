'use strict';

define(
  [
    'flight/lib/component',
    'component_data/dependency',
    'component_data/momentAnnotations'
  ],

  function (defineComponent, dependency, getMomentAnnotations) {
    return defineComponent(serviceDataModal);

    function serviceDataModal() {
      this.after('initialize', function () {
        this.on(document, 'showServiceDataModal', this.showServiceDataModal);
        this.on(document, 'showDependencyModal', this.showDependencyModal);
        this.on(document, 'serviceDataReceived', renderServiceDataModal);
        this.on(document, 'dependencyDataReceived', renderDependencyModal);
      });

      this.showServiceDataModal = function (event, data) {
        this.trigger(document, 'serviceDataRequested', {
          serviceName: data.serviceName
        });
      };

      this.showDependencyModal = function (event, data) {
        this.trigger(document, 'dependencyDataRequested', {
          parent: data.parent,
          child: data.child
        });
      }
    }

    function renderDependencyModal(event, data) {
      var moments = getMomentAnnotations(data.durationMoments);

      var $modal = $('#dependencyModal');
      var $parentElement = $('<a href="">' + data.parent + '</a>');
      $parentElement.click(function (ev) {
        ev.preventDefault();
        this.trigger(document, 'showServiceDataModal', {
          serviceName: data.parent
        });
      }.bind(this));

      var $childElement = $('<a href="">' + data.child + '</a>');
      $childElement.click(function (ev) {
        ev.preventDefault();
        this.trigger(document, 'showServiceDataModal', {
          serviceName: data.child
        });
      }.bind(this));

      $modal.find('#dependencyModalParent').html($parentElement);
      $modal.find('#dependencyModalChild').html($childElement);

      $modal.find('#dependencyNumCalls').text(moments.count);
      $modal.find('#dependencyMean').text(moments.mean);
      $modal.find('#dependencyVariance').text(moments.variance);
      $modal.find('#dependencyStddev').text(moments.stddev);
      $modal.find('#dependencySkewness').text(moments.skewness);
      $modal.find('#dependencyKurtosis').text(moments.kurtosis);

      $('#serviceModal').modal('hide');
      $modal.modal('show');
    }

    function renderServiceDataModal(event, data) {
      var $modal = $('#serviceModal');
      $modal.find('#serviceUsedByList').html('');
      data.usedBy.sort(function (a, b) {
        return a.toLowerCase().localeCompare(b.toLowerCase());
      });
      data.usedBy.forEach(function (usedBy) {
        var $name = $('<li><a href="">' + usedBy + '</a></li>');
        $name.find('a').click(function (ev) {
          ev.preventDefault();
          this.trigger(document, 'showDependencyModal', {
            parent: usedBy,
            child: data.serviceName
          });
        }.bind(this));
        $modal.find('#serviceUsedByList').append($name);
      }.bind(this));

      $modal.find('#serviceUsesList').html('');
      data.uses.sort(function (a, b) {
        return a.toLowerCase().localeCompare(b.toLowerCase());
      });

      data.uses.forEach(function (uses) {
        var $name = $('<li><a href="">' + uses + '</a></li>');
        $name.find('a').click(function (ev) {
          ev.preventDefault();
          this.trigger(document, 'showDependencyModal', {
            parent: data.serviceName,
            child: uses
          });
        }.bind(this));
        $modal.find('#serviceUsesList').append($name);
      }.bind(this));

      $modal.find('#serviceModalTitle').text(data.serviceName);

      $modal.modal('show');
      $('#dependencyModal').modal('hide');
    }
  }
);
