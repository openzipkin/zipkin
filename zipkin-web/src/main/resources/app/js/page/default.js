'use strict';

define(
  [
    'component_data/spanNames',
    'component_ui/serviceName',
    'component_ui/spanName',
    'component_ui/infoPanel',
    'component_ui/infoButton',
    'component_ui/traceFilters',
    'component_ui/traces'
  ],

  function (
    SpanNamesData,
    ServiceNameUI,
    SpanNameUI,
    InfoPanelUI,
    InfoButtonUI,
    TraceFiltersUI,
    TracesUI
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

      $('.date-input').datepicker({format: 'mm-dd-yyyy'});
      $('.timeago').timeago();
    }
  }
);
