import {component} from 'flightjs';
import $ from 'jquery';
import TraceData from '../component_data/trace';
import FilterAllServicesUI from '../component_ui/filterAllServices';
import FullPageSpinnerUI from '../component_ui/fullPageSpinner';
import ServiceFilterSearchUI from '../component_ui/serviceFilterSearch';
import SpanPanelUI from '../component_ui/spanPanel';
import TraceUI from '../component_ui/trace';
import FilterLabelUI from '../component_ui/filterLabel';
import ZoomOut from '../component_ui/zoomOutSpans';
import {traceTemplate} from '../templates';

const TracePageComponent = component(function TracePage() {
  this.after('initialize', function() {
    window.document.title = 'Zipkin - Traces';

    TraceData.attachTo(document, {
      traceId: this.attr.traceId
    });
    this.on(document, 'tracePageModelView', function(ev, modelview) {
      this.$node.html(traceTemplate(modelview));

      FilterAllServicesUI.attachTo('#filterAllServices', {
        totalServices: $('.trace-details.services span').length
      });
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

export default function initializeTrace(traceId) {
  TracePageComponent.attachTo('.content', {
    traceId
  });
}
