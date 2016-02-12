'use strict';

define(
  [
    '../component_ui/environment',
    '../component_ui/filterAllServices',
    '../component_ui/fullPageSpinner',
    '../component_ui/serviceFilterSearch',
    '../component_ui/spanPanel',
    '../component_ui/trace',
    '../component_ui/zoomOutSpans'
  ],

  function (
    {environment: EnvironmentUI},
    FilterAllServicesUI,
    FullPageSpinnerUI,
    ServiceFilterSearchUI,
    SpanPanelUI,
    TraceUI,
    ZoomOut
  ) {

    return initialize;

    function initialize() {
      window.document.title = 'Zipkin - Traces';

      EnvironmentUI.attachTo('#environment');
      FilterAllServicesUI.attachTo('#filterAllServices', {totalServices: $('.trace-details.services span').length});
      FullPageSpinnerUI.attachTo('#fullPageSpinner');
      ServiceFilterSearchUI.attachTo('#serviceFilterSearch');
      SpanPanelUI.attachTo('#spanPanel');
      TraceUI.attachTo('#trace-container');
      ZoomOut.attachTo('#zoomOutSpans');

      $('.annotation:not(.core)').tooltip({placement: 'left'});
    }
  }
);
