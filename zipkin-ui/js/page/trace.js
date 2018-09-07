import {component} from 'flightjs';
import $ from 'jquery';
import TraceData from '../component_data/trace';
import FilterAllServicesUI from '../component_ui/filterAllServices';
import FullPageSpinnerUI from '../component_ui/fullPageSpinner';
import JsonPanelUI from '../component_ui/jsonPanel';
import ServiceFilterSearchUI from '../component_ui/serviceFilterSearch';
import SpanPanelUI from '../component_ui/spanPanel';
import TraceUI from '../component_ui/trace';
import FilterLabelUI from '../component_ui/filterLabel';
import ZoomOut from '../component_ui/zoomOutSpans';
import {traceTemplate} from '../templates';
import {contextRoot} from '../publicPath';

const TracePageComponent = component(function TracePage() {
  this.after('initialize', function() {
    window.document.title = 'Zipkin - Traces';
    $('body').tooltip({
      selector: '[data-toggle="tooltip"]'
    });
    TraceData.attachTo(document, {
      traceId: this.attr.traceId,
      logsUrl: this.attr.config('logsUrl')
    });
    this.on(document, 'tracePageModelView', function(ev, data) {
      this.$node.html(traceTemplate({
        contextRoot,
        ...data.modelview
      }));

      FilterAllServicesUI.attachTo('#filterAllServices', {
        totalServices: $('.trace-details.services span').length
      });
      FullPageSpinnerUI.attachTo('#fullPageSpinner');
      JsonPanelUI.attachTo('#jsonPanel');
      ServiceFilterSearchUI.attachTo('#serviceFilterSearch');
      SpanPanelUI.attachTo('#spanPanel');
      TraceUI.attachTo('#trace-container');
      FilterLabelUI.attachTo('.service-filter-label');
      ZoomOut.attachTo('#zoomOutSpans');

      this.$node.find('#traceJsonLink').click(e => {
        e.preventDefault();
        this.trigger('uiRequestJsonPanel', {
          title: `Trace ${this.attr.traceId}`,
          obj: data.trace,
          link: `${contextRoot}api/v2/trace/${this.attr.traceId}`
        });
      });

      $('.annotation:not(.core)').tooltip({placement: 'left'});
    });
  });
});

export default function initializeTrace(traceId, config) {
  TracePageComponent.attachTo('.content', {
    traceId,
    config
  });
}
