import {component} from 'flightjs';
import EnvironmentUI from '../component_ui/environment';
import NavbarUI from '../component_ui/navbar';
import {layoutTemplate} from '../templates';
import GoToTraceUI from '../component_ui/goToTrace';

export default component(function CommonUI() {
  this.after('initialize', function() {
    this.$node.html(layoutTemplate());
    NavbarUI.attachTo('#navbar');
    EnvironmentUI.attachTo('#environment', {config: this.attr.config});
    GoToTraceUI.attachTo('#traceIdQueryForm');
  });
});
