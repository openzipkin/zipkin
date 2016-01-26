'use strict';

define(
  [
    'component_data/dependency',
    'component_ui/dependencyGraph',
    'component_ui/serviceDataModal',
    'component_ui/timeStamp',
    'component_ui/goToDependency'
  ],

  function (DependencyData,
            DependencyGraphUI,
            ServiceDataModal,
            TimeStampUI,
            GoToDependencyUI) {

    return initialize;

    function initialize() {
      DependencyData.attachTo('#dependency-container');
      DependencyGraphUI.attachTo('#dependency-container');
      ServiceDataModal.attachTo('#service-data-modal-container');
      $('#endTs').val(moment().valueOf());
      $('#startTs').val(moment().valueOf() - 7*24*60*60*1000); // set default startTs 7 days ago
      TimeStampUI.attachTo('#end-ts');
      TimeStampUI.attachTo('#start-ts');
      GoToDependencyUI.attachTo('#dependency-query-form');
    }
  }
);
