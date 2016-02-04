'use strict';

define(
  [
    'moment',
    '../component_data/dependency',
    '../component_ui/dependencyGraph',
    '../component_ui/serviceDataModal',
    '../component_ui/timeStamp',
    '../component_ui/goToDependency'
  ],

  function (moment,
            DependencyData,
            DependencyGraphUI,
            ServiceDataModal,
            TimeStampUI,
            GoToDependencyUI) {

    return initialize;

    function initialize() {
      // parse query string to set form
      var params = {}, postBody = location.search.substring(1), regex = /([^&=]+)=([^&]*)/g, m;
      while (m = regex.exec(postBody)) {
        params[decodeURIComponent(m[1])] = decodeURIComponent(m[2]);
      }
      var endTs = params['endTs'] || moment().valueOf();
      var startTs = params['startTs'] || moment().valueOf() - 7*24*60*60*1000; // set default startTs 7 days ago
      $('#endTs').val(endTs);
      $('#startTs').val(startTs);

      DependencyData.attachTo('#dependency-container');
      DependencyGraphUI.attachTo('#dependency-container');
      ServiceDataModal.attachTo('#service-data-modal-container');
      TimeStampUI.attachTo('#end-ts');
      TimeStampUI.attachTo('#start-ts');
      GoToDependencyUI.attachTo('#dependency-query-form');
    }
  }
);
