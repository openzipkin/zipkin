'use strict';

define(
  [
    'component_ui/filterAllServices',
    'component_ui/fullPageSpinner',
    'component_ui/serviceFilterSearch',
    'component_ui/spanPanel',
    'component_ui/trace'
  ],

  function (
    FilterAllServicesUI,
    FullPageSpinnerUI,
    ServiceFilterSearchUI,
    SpanPanelUI,
    TraceUI
  ) {

    return initialize;

    function initialize() {
      FilterAllServicesUI.attachTo('#filterAllServices', {totalServices: $('.trace-details.services span').length});
      FullPageSpinnerUI.attachTo('#fullPageSpinner');
      ServiceFilterSearchUI.attachTo('#serviceFilterSearch');
      SpanPanelUI.attachTo('#spanPanel');
      TraceUI.attachTo('#trace-container');

      $('.annotation:not(.core)').tooltip({placement: 'left'});
    }
  }
);
