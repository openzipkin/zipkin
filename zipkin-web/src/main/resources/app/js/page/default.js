'use strict';

define(
  [
    'flightjs',
    'timeago',
    'query-string',
    '../component_data/default',
    '../component_data/spanNames',
    '../component_data/serviceNames',
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
    queryString,
    {DefaultData},
    SpanNamesData,
    ServiceNamesData,
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
        this.trigger(document, 'navigate', {route: 'index'});

        const query = queryString.parse(window.location.search);

        this.on(document, 'defaultPageModelView', function(ev, modelView) {
          const limit = query.limit || window.config.queryLimit;
          const minDuration = query.minDuration;
          const endTs = query.endTs || new Date().getTime();
          const serviceName = query.serviceName || '';
          const annotationQuery = query.annotationQuery || '';
          const queryWasPerformed = serviceName && serviceName.length > 0;
          this.$node.html(defaultTemplate({
            limit,
            minDuration,
            endTs,
            serviceName,
            annotationQuery,
            queryWasPerformed,
            count: modelView.traces.length,
            ...modelView
          }));

          SpanNamesData.attachTo(document);
          ServiceNamesData.attachTo(document);
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

        DefaultData.attachTo(document);
      });
    });

    return function initializeDefault() {
      DefaultPageComponent.attachTo('.content');
    };
  }
);
