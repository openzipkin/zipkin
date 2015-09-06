'use strict';

define(
  [
    'component_data/dependency',
    'component_ui/dependencyGraph',
    'component_ui/serviceDataModal'
  ],

  function (DependencyData,
            DependencyGraphUI,
            ServiceDataModal) {

    return initialize;

    function initialize() {
      DependencyData.attachTo('#dependency-container');
      DependencyGraphUI.attachTo('#dependency-container');
      ServiceDataModal.attachTo('#service-data-modal-container');
    }
  }
);