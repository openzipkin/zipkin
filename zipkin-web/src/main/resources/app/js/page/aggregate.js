'use strict';

define(
  [
    'component_data/aggregate',
    'component_ui/dependencyGraph',
    'component_ui/serviceDataModal'
  ],

  function (AggregateData,
            DependencyGraphUI,
            ServiceDataModal) {

    return initialize;

    function initialize() {
      AggregateData.attachTo('#aggregate-container');
      DependencyGraphUI.attachTo('#aggregate-container');
      ServiceDataModal.attachTo('#service-data-modal-container');
    }
  }
);