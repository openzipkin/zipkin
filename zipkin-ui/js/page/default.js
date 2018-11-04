import {component} from 'flightjs';
import $ from 'jquery';
import timeago from 'timeago'; // eslint-disable-line no-unused-vars
import queryString from 'query-string';
import DefaultData from '../component_data/default';
import SpanNamesData from '../component_data/spanNames';
import ServiceNamesData from '../component_data/serviceNames';
import ServiceNameUI from '../component_ui/serviceName';
import SpanNameUI from '../component_ui/spanName';
import LookbackUI from '../component_ui/lookback';
import InfoPanelUI from '../component_ui/infoPanel';
import InfoButtonUI from '../component_ui/infoButton';
import JsonPanelUI from '../component_ui/jsonPanel';
import TraceFiltersUI from '../component_ui/traceFilters';
import TracesUI from '../component_ui/traces';
import TimeStampUI from '../component_ui/timeStamp';
import BackToTop from '../component_ui/backToTop';
import {defaultTemplate} from '../templates';
import {searchDisabled} from '../templates';
import {contextRoot} from '../publicPath';
import {i18nInit} from '../component_ui/i18n';
import bootstrap // eslint-disable-line no-unused-vars
    from 'bootstrap/dist/js/bootstrap.bundle.min.js';

const DefaultPageComponent = component(function DefaultPage() {
  const sortOptions = [
    {value: 'service-percentage-desc', text: 'Service Percentage: Longest First'},
    {value: 'service-percentage-asc', text: 'Service Percentage: Shortest First'},
    {value: 'duration-desc', text: 'Longest First'},
    {value: 'duration-asc', text: 'Shortest First'},
    {value: 'timestamp-desc', text: 'Newest First'},
    {value: 'timestamp-asc', text: 'Oldest First'}
  ];

  const sortSelected = function getSelector(selectedSortValue) {
    return function selected() {
      if (this.value === selectedSortValue) {
        return 'selected';
      }
      return '';
    };
  };

  this.after('initialize', function() {
    window.document.title = 'Zipkin - Index';
    if (!this.attr.config('searchEnabled')) {
      this.$node.html(searchDisabled());
      return;
    }

    this.trigger(document, 'navigate', {route: 'zipkin/index'});

    const query = queryString.parse(window.location.search);

    this.on(document, 'defaultPageModelView', function(ev, modelView) {
      const limit = query.limit || this.attr.config('queryLimit');
      const minDuration = query.minDuration;
      const endTs = query.endTs || new Date().getTime();
      const startTs = query.startTs || (endTs - this.attr.config('defaultLookback'));
      const serviceName = query.serviceName || '';
      const annotationQuery = query.annotationQuery || '';
      const sortOrder = query.sortOrder || 'duration-desc';
      const queryWasPerformed = serviceName && serviceName.length > 0;
      this.$node.html(defaultTemplate({
        limit,
        minDuration,
        startTs,
        endTs,
        serviceName,
        annotationQuery,
        queryWasPerformed,
        contextRoot,
        traceCount: modelView.traces.length,
        sortOrderOptions: sortOptions,
        sortOrderSelected: sortSelected(sortOrder),
        apiURL: modelView.apiURL,
        ...modelView
      }));

      SpanNamesData.attachTo(document);
      ServiceNamesData.attachTo(document);
      ServiceNameUI.attachTo('#serviceName');
      SpanNameUI.attachTo('#spanName');
      LookbackUI.attachTo('#lookback');
      InfoPanelUI.attachTo('#infoPanel');
      InfoButtonUI.attachTo('button.info-request');
      JsonPanelUI.attachTo('#jsonPanel');
      TraceFiltersUI.attachTo('#trace-filters');
      TracesUI.attachTo('#traces');
      TimeStampUI.attachTo('#end-ts');
      TimeStampUI.attachTo('#start-ts');
      BackToTop.attachTo('#backToTop');
      i18nInit('traces');
      $('.timeago').timeago();
      // Need to initialize the datepicker when the UI refershes. Can be optimized
      this.$date = this.$node.find('.date-input');
      this.$date.datepicker({format: 'yyyy-mm-dd'});
      this.$node.find('#rawResultsJsonLink').click(e => {
        e.preventDefault();
        this.trigger('uiRequestJsonPanel', {
          title: 'Search Results',
          obj: modelView.rawResponse,
          link: modelView.apiURL
        });
      });
    });
    DefaultData.attachTo(document);
  });
});

export default function initializeDefault(config) {
  DefaultPageComponent.attachTo('.content', {config});
}
