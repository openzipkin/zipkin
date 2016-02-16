'use strict';

define(
  [
    'flight',
    'timeago',
    '../component_data/default',
    '../component_data/spanNames',
    '../component_data/serviceNames',
    '../component_ui/environment',
    '../component_ui/serviceName',
    '../component_ui/spanName',
    '../component_ui/infoPanel',
    '../component_ui/infoButton',
    '../component_ui/traceFilters',
    '../component_ui/traces',
    '../component_ui/timeStamp',
    '../component_ui/backToTop',
    '../component_ui/goToTrace',
    '../../../templates/v2/index.mustache'
  ],

  function (
    {component},
    timeago,
    {DefaultData},
    SpanNamesData,
    ServiceNamesData,
    {environment: EnvironmentUI},
    ServiceNameUI,
    SpanNameUI,
    InfoPanelUI,
    InfoButtonUI,
    TraceFiltersUI,
    TracesUI,
    TimeStampUI,
    BackToTop,
    GoToTraceUI,
    defaultTemplate
  ) {

    const DefaultPageComponent = component(function DefaultPage() {
      this.after('initialize', function() {
        window.document.title = 'Zipkin - Index';
        DefaultData.attachTo(document);

        this.on(document, 'defaultPageModelView', function(ev, modelView) {
          this.$node.html(defaultTemplate(modelView));

          SpanNamesData.attachTo(document);
          ServiceNamesData.attachTo(document);
          EnvironmentUI.attachTo('#environment');
          ServiceNameUI.attachTo('#serviceName');
          SpanNameUI.attachTo('#spanName');
          InfoPanelUI.attachTo('#infoPanel');
          InfoButtonUI.attachTo('button.info-request');
          TraceFiltersUI.attachTo('#trace-filters');
          TracesUI.attachTo('#traces');
          TimeStampUI.attachTo('#time-stamp');
          BackToTop.attachTo('#backToTop');
          GoToTraceUI.attachTo('#traceIdQueryForm');

          $('.timeago').timeago();
        });
      });
    });

    return function initializeDefault() {
      DefaultPageComponent.attachTo('.content');
    };
  }
);
