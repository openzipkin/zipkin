import {component} from 'flightjs';
import $ from 'jquery';
import bootstrap // eslint-disable-line no-unused-vars
from 'bootstrap-sass/assets/javascripts/bootstrap.js';
import TracesUI from './traces';
import {tracesTemplate} from '../templates';

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
  this.attributes({
    filterForm: '#filterForm',
    selectlimit: ':input[name=limit]',
    selecterorr: '#errorradio :selected'
  });

  this.showServiceDataModal = function(event, data) {
    this.trigger(document, 'serviceDataRequested', {
      serviceName: data.serviceName
    });
  };

  this.showTracesForLinkedServices = function(event, data) {
    this.trigger(document, 'filterLinkDataRequested', {
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
  this.renderItems = function(event, modelView) {
    event.preventDefault();
    event.stopPropagation();
    $('#traces').html(tracesTemplate({
      ...modelView
    }));
    TracesUI.attachTo('#traces');
  };

  this.filterLinkDependency = function(event) {
    event.preventDefault();
    event.stopPropagation();
    // TODO: Find a better way to get the filter values
    this.trigger(document, 'filterLinkDataRequested', {
      limit: document.getElementById('limit').value,
      error: document.getElementById('erroronly').checked || false,
      parentService: document.getElementById('dependencyModalParent').children[0].text,
      childService: document.getElementById('dependencyModalChild').children[0].text,
      serviceName: document.getElementById('serviceModalTitle').textContent
    });
  };
  this.after('initialize', function() {
    this.on(document, 'showServiceDataModal', this.showServiceDataModal);
    this.on(document, 'showDependencyModal', this.showDependencyModal);
    this.on(document, 'serviceDataReceived', renderServiceDataModal);
    this.on(document, 'parentChildDataReceived', renderDependencyModal);
    this.on('#filterTraceBtn', 'click', {
      filterForm: this.filterLinkDependency,
    });
    this.on(document, 'filterLinkDataRecieved', this.renderItems);
  });
});
