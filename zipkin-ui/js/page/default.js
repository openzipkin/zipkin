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
import JsonPanelUI from '../component_ui/jsonPanel';
import TraceFiltersUI from '../component_ui/traceFilters';
import TracesUI from '../component_ui/traces';
import TimeStampUI from '../component_ui/timeStamp';
import BackToTop from '../component_ui/backToTop';
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
      const startTs = query.startTs || (endTs - this.attr.config('defaultLookback'));
      const serviceName = query.serviceName || '';
      const annotationQuery = query.annotationQuery || '';
      const queryWasPerformed = serviceName && serviceName.length > 0;
      this.$node.html(defaultTemplate({
        limit,
        minDuration,
        startTs,
        endTs,
        serviceName,
        annotationQuery,
        queryWasPerformed,
        count: modelView.traces.length,
        apiURL: modelView.apiURL,
        ...modelView
      }));

      SpanNamesData.attachTo(document);
      ServiceNamesData.attachTo(document);
      ServiceNameUI.attachTo('#serviceName');
      SpanNameUI.attachTo('#spanName');
      InfoPanelUI.attachTo('#infoPanel');
      InfoButtonUI.attachTo('button.info-request');
      JsonPanelUI.attachTo('#jsonPanel');
      TraceFiltersUI.attachTo('#trace-filters');
      TracesUI.attachTo('#traces');
      TimeStampUI.attachTo('#end-ts');
      TimeStampUI.attachTo('#start-ts');
      BackToTop.attachTo('#backToTop');

      $('.timeago').timeago();

      this.$node.find('#rawResultsJsonLink').click(e => {
        e.preventDefault();
        this.trigger('uiRequestJsonPanel', {title: 'Search Results',
                                            obj: modelView.rawResponse,
                                            link: modelView.apiURL});
      });
    });

    DefaultData.attachTo(document);
  });
});

export default function initializeDefault(config) {
  DefaultPageComponent.attachTo('.content', {config});
}
