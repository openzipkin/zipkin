import {component} from 'flightjs';
import $ from 'jquery';
import timeago from 'timeago'; // eslint-disable-line no-unused-vars
import queryString from 'query-string';
import DefaultData from '../component_data/default';
import SpanNamesData from '../component_data/spanNames';
import ServiceNamesData from '../component_data/serviceNames';
import ServiceNameUI from '../component_ui/serviceName';
import SpanNameUI from '../component_ui/spanName';
import InfoPanelUI from '../component_ui/infoPanel';
import InfoButtonUI from '../component_ui/infoButton';
import TraceFiltersUI from '../component_ui/traceFilters';
import TracesUI from '../component_ui/traces';
import TimeStampUI from '../component_ui/timeStamp';
import BackToTop from '../component_ui/backToTop';
import GoToTraceUI from '../component_ui/goToTrace';
import {defaultTemplate} from '../templates';

const DefaultPageComponent = component(function DefaultPage() {
  this.after('initialize', function() {
    window.document.title = 'Zipkin - Index';
    this.trigger(document, 'navigate', {route: 'index'});

    const query = queryString.parse(window.location.search);

    this.on(document, 'defaultPageModelView', function(ev, modelView) {
      const limit = query.limit || this.attr.config('queryLimit');
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

export default function initializeDefault(config) {
  DefaultPageComponent.attachTo('.content', {config});
}
