'use strict';

define(
  [
    'component_data/spanNames',
    'component_ui/serviceName',
    'component_ui/spanName',
    'component_ui/infoPanel',
    'component_ui/infoButton',
    'component_ui/traceFilters',
    'component_ui/traces',
    'component_ui/timeStamp',
    'component_ui/backToTop'
  ],

  function (
    SpanNamesData,
    ServiceNameUI,
    SpanNameUI,
    InfoPanelUI,
    InfoButtonUI,
    TraceFiltersUI,
    TracesUI,
    TimeStampUI,
    BackToTop
  ) {

    return initialize;

    function initialize() {
      SpanNamesData.attachTo(document);
      ServiceNameUI.attachTo('#serviceName');
      SpanNameUI.attachTo('#spanName');
      InfoPanelUI.attachTo('#infoPanel');
      InfoButtonUI.attachTo('button.info-request');
      TraceFiltersUI.attachTo('#trace-filters');
      TracesUI.attachTo('#traces');
      TimeStampUI.attachTo('#time-stamp');
      BackToTop.attachTo('#backToTop');

      $('.timeago').timeago();
    }
  }
);
