import {component} from 'flightjs';
import EnvironmentUI from '../component_ui/environment';
import ErrorUI from '../component_ui/error';
import NavbarUI from '../component_ui/navbar';
import {layoutTemplate} from '../templates';
import GoToTraceUI from '../component_ui/goToTrace';
import GoToLensUI from '../component_ui/goToLens';
import {contextRoot} from '../publicPath';

export default component(function CommonUI() {
  this.after('initialize', function() {
    const suggestLens = this.attr.config && this.attr.config('suggestLens');
    this.$node.html(layoutTemplate({contextRoot, suggestLens}));
    NavbarUI.attachTo('#navbar');
    ErrorUI.attachTo('#errorPanel');
    EnvironmentUI.attachTo('#environment', {config: this.attr.config});
    GoToTraceUI.attachTo('#traceIdQueryForm');
    GoToLensUI.attachTo('#lensForm');
  });
});
