import {component} from 'flightjs';
import EnvironmentUI from '../component_ui/environment';
import ErrorUI from '../component_ui/error';
import NavbarUI from '../component_ui/navbar';
import {layoutTemplate} from '../templates';
import GoToTraceUI from '../component_ui/goToTrace';
import {contextRoot} from '../publicPath';

export default component(function CommonUI() {
  this.after('initialize', function() {
    let archiveHome = undefined;
    if (this.attr && this.attr.config) {
      archiveHome = this.attr.config('archiveHome');
    }
    this.$node.html(layoutTemplate({contextRoot, archiveHome}));
    NavbarUI.attachTo('#navbar');
    ErrorUI.attachTo('#errorPanel');
    EnvironmentUI.attachTo('#environment', {config: this.attr.config});
    GoToTraceUI.attachTo('#traceIdQueryForm');
  });
});
