import {component} from 'flightjs';
import $ from 'jquery';
import bootstrap // eslint-disable-line no-unused-vars
  from 'bootstrap/dist/js/bootstrap.js';

function renderDependencyModal(event, data) {
  const $modal = $('#dependencyModal');
  const $parentElement = $(`<a href="">${data.parent}</a>`);
  $parentElement.click(ev => {
    ev.preventDefault();
    this.trigger(document, 'showServiceDataModal', {
      serviceName: data.parent
    });
  });

  const $childElement = $(`<a href="">${data.child}</a>`);
  $childElement.click(ev => {
    ev.preventDefault();
    this.trigger(document, 'showServiceDataModal', {
      serviceName: data.child
    });
  });

  $modal.find('#dependencyModalParent').html($parentElement);
  $modal.find('#dependencyModalChild').html($childElement);
  $modal.find('#dependencyCallCount').text(data.callCount);
  $modal.find('#dependencyErrorCount').text(data.errorCount || 0);

  $('#serviceModal').modal('hide');
  $modal.modal('show');
}

function renderServiceDataModal(event, data) {
  const $modal = $('#serviceModal');
  $modal.find('#serviceUsedByList').html('');
  data.usedBy.sort((a, b) =>
    a.toLowerCase().localeCompare(b.toLowerCase())
  );
  data.usedBy.forEach(usedBy => {
    const $name = $(`<li><a href="">${usedBy}</a></li>`);
    $name.find('a').click(ev => {
      ev.preventDefault();
      this.trigger(document, 'showDependencyModal', {
        parent: usedBy,
        child: data.serviceName
      });
    });
    $modal.find('#serviceUsedByList').append($name);
  });

  $modal.find('#serviceUsesList').html('');
  data.uses.sort((a, b) =>
    a.toLowerCase().localeCompare(b.toLowerCase())
  );

  data.uses.forEach(uses => {
    const $name = $(`<li><a href="">${uses}</a></li>`);
    $name.find('a').click(ev => {
      ev.preventDefault();
      this.trigger(document, 'showDependencyModal', {
        parent: data.serviceName,
        child: uses
      });
    });
    $modal.find('#serviceUsesList').append($name);
  });

  $modal.find('#serviceModalTitle').text(data.serviceName);

  $modal.modal('show');
  $('#dependencyModal').modal('hide');
}

export default component(function serviceDataModal() {
  this.showServiceDataModal = function(event, data) {
    this.trigger(document, 'serviceDataRequested', {
      serviceName: data.serviceName
    });
  };

  this.showDependencyModal = function(event, data) {
    this.trigger(document, 'parentChildDataRequested', {
      parent: data.parent,
      child: data.child,
      callCount: data.callCount
    });
  };

  this.after('initialize', function() {
    this.on(document, 'showServiceDataModal', this.showServiceDataModal);
    this.on(document, 'showDependencyModal', this.showDependencyModal);
    this.on(document, 'serviceDataReceived', renderServiceDataModal);
    this.on(document, 'parentChildDataReceived', renderDependencyModal);
  });
});
