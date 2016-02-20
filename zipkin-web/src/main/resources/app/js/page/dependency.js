'use strict';

define(
  [
    'moment',
    'flight',
    'jquery',
    'query-string',
    '../component_data/dependency',
    '../component_ui/dependencyGraph',
    '../component_ui/serviceDataModal',
    '../component_ui/timeStamp',
    '../component_ui/goToDependency',
    '../../../templates/v2/dependency.mustache'
  ],

  function (moment,
            {component},
            $,
            queryString,
            DependencyData,
            DependencyGraphUI,
            ServiceDataModal,
            TimeStampUI,
            GoToDependencyUI,
            dependenciesTemplate
  ) {

    const DependencyPageComponent = component(function DependencyPage() {
      this.after('initialize', function() {
        window.document.title = 'Zipkin - Dependency';
        this.trigger(document, 'navigate', {route: 'dependency'});

        this.$node.html(dependenciesTemplate());

        const {startTs, endTs} = queryString.parse(location.search);
        $('#endTs').val(endTs || moment().valueOf());
        $('#startTs').val(startTs || moment().valueOf() - 7*24*60*60*1000); // set default startTs 7 days ago;

        DependencyData.attachTo('#dependency-container');
        DependencyGraphUI.attachTo('#dependency-container');
        ServiceDataModal.attachTo('#service-data-modal-container');
        TimeStampUI.attachTo('#end-ts');
        TimeStampUI.attachTo('#start-ts');
        GoToDependencyUI.attachTo('#dependency-query-form');
      });
    });

    return function initializeDependencies() {
      DependencyPageComponent.attachTo('.content');
    }
  }
);
