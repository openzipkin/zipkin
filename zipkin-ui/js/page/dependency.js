import moment from 'moment';
import {component} from 'flightjs';
import $ from 'jquery';
import queryString from 'query-string';
import DependencyData from '../component_data/dependency';
import DependencyGraphUI from '../component_ui/dependencyGraph';
import ServiceDataModal from '../component_ui/serviceDataModal';
import TimeStampUI from '../component_ui/timeStamp';
import GoToDependencyUI from '../component_ui/goToDependency';
import {dependenciesTemplate} from '../templates';

const DependencyPageComponent = component(function DependencyPage() {
  this.after('initialize', function() {
    window.document.title = 'Zipkin - Dependency';
    this.trigger(document, 'navigate', {route: 'dependency'});

    this.$node.html(dependenciesTemplate());

    const {startTs, endTs} = queryString.parse(location.search);
    $('#endTs').val(endTs || moment().valueOf());
    // When #1185 is complete, the only visible granularity is day
    $('#startTs').val(startTs || moment().valueOf() - 86400000);

    DependencyData.attachTo('#dependency-container');
    DependencyGraphUI.attachTo('#dependency-container');
    ServiceDataModal.attachTo('#service-data-modal-container');
    TimeStampUI.attachTo('#end-ts');
    TimeStampUI.attachTo('#start-ts');
    GoToDependencyUI.attachTo('#dependency-query-form');
  });
});

export default function initializeDependencies(config) {
  DependencyPageComponent.attachTo('.content', {config});
}
