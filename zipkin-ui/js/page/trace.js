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

    TraceData.attachTo(document, {
      traceId: this.attr.traceId,
      logsUrl: this.attr.config('logsUrl'),
      archiveEndpoint: this.attr.config('archiveEndpoint'),
      archiveReadEndpoint: this.attr.config('archiveReadEndpoint')
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
          link: `${contextRoot}api/v1/trace/${this.attr.traceId}`
        });
      });

      this.$node.find('#archiveTraceLink').click(e => {
        e.preventDefault();
        const traceId = this.attr.traceId;
        const archiveEndpoint = this.attr.config('archiveEndpoint');
        const archiveReadEndpoint = this.attr.config('archiveReadEndpoint');

        $.ajax(`/api/v2/trace/${traceId}`, {
          type: 'GET',
          dataType: 'json'
        }).done(trace => {
          $.ajax(`${archiveEndpoint}`, {
            type: 'POST',
            dataType: 'json',
            crossDomain: true,
            data: JSON.stringify(trace),
            contentType: 'application/json; charset=utf-8'
          }).done(result => {
            console.log(result);
            if (archiveReadEndpoint) {
              window.prompt('Trace archived. Copy link to clipboard: Cmd+C, Enter',
                `${archiveReadEndpoint}/${traceId}`);
            } else {
              alert('Trace archived');
            }
          }).fail(error => {
            console.log(error);
            alert(`Unable to save trace ${this.attr.traceId}`);
          });
        }).fail(error2 => {
          console.log(error2);
          alert(`Unable to save trace ${this.attr.traceId}`);
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
