'use strict';

define(
  [
    'timeago',
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
    '../component_ui/goToTrace'
  ],

  function (
    timeago,
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
    GoToTraceUI
  ) {

    return initialize;

    function initialize() {
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
    }
  }
);
