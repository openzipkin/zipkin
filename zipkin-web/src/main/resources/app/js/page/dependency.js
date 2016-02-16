'use strict';

define(
  [
    'moment',
    'jquery',
    'query-string',
    '../component_data/dependency',
    '../component_ui/environment',
    '../component_ui/dependencyGraph',
    '../component_ui/serviceDataModal',
    '../component_ui/timeStamp',
    '../component_ui/goToDependency',
    '../../../templates/v2/dependency.mustache'
  ],

  function (moment,
            $,
            queryString,
            DependencyData,
            {environment: EnvironmentUI},
            DependencyGraphUI,
            ServiceDataModal,
            TimeStampUI,
            GoToDependencyUI,
            dependenciesTemplate
  ) {

    return initialize;

    function initialize() {
      window.document.title = 'Zipkin - Dependency';
      $('.content').html(dependenciesTemplate());

      const {startTs, endTs} = queryString.parse(location.search);
      $('#endTs').val(endTs || moment().valueOf());
      $('#startTs').val(startTs || moment().valueOf() - 7*24*60*60*1000); // set default startTs 7 days ago;

      EnvironmentUI.attachTo('#environment');
      DependencyData.attachTo('#dependency-container');
      DependencyGraphUI.attachTo('#dependency-container');
      ServiceDataModal.attachTo('#service-data-modal-container');
      TimeStampUI.attachTo('#end-ts');
      TimeStampUI.attachTo('#start-ts');
      GoToDependencyUI.attachTo('#dependency-query-form');
    }
  }
);
