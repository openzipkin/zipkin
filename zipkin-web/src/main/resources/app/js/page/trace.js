'use strict';

define(
  [
    'flight',
    '../component_data/trace',
    '../component_ui/filterAllServices',
    '../component_ui/fullPageSpinner',
    '../component_ui/serviceFilterSearch',
    '../component_ui/spanPanel',
    '../component_ui/trace',
    '../component_ui/filterLabel',
    '../component_ui/zoomOutSpans',
    '../../../templates/v2/trace.mustache'
  ],

  function (
    {component},
    {TraceData},
    FilterAllServicesUI,
    FullPageSpinnerUI,
    ServiceFilterSearchUI,
    SpanPanelUI,
    TraceUI,
    FilterLabelUI,
    ZoomOut,
    tracetemplate
  ) {

    const TracePageComponent = component(function TracePage() {
      this.after('initialize', function() {
        window.document.title = 'Zipkin - Traces';

        TraceData.attachTo(document);
        this.on(document, 'tracePageModelView', function(ev, modelview) {
          this.$node.html(tracetemplate(modelview));

          FilterAllServicesUI.attachTo('#filterAllServices', {totalServices: $('.trace-details.services span').length});
          FullPageSpinnerUI.attachTo('#fullPageSpinner');
          ServiceFilterSearchUI.attachTo('#serviceFilterSearch');
          SpanPanelUI.attachTo('#spanPanel');
          TraceUI.attachTo('#trace-container');
          FilterLabelUI.attachTo('.service-filter-label');
          ZoomOut.attachTo('#zoomOutSpans');

          $('.annotation:not(.core)').tooltip({placement: 'left'});
        });
      });
    });

    return function initializeTrace() {
      TracePageComponent.attachTo('.content');
    }
  }
);
