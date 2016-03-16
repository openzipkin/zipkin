import {component} from 'flightjs';
import EnvironmentUI from '../component_ui/environment';
import NavbarUI from '../component_ui/navbar';
import {layoutTemplate} from '../templates';

export default component(function CommonUI() {
  this.after('initialize', function() {
    this.$node.html(layoutTemplate());
    NavbarUI.attachTo('#navbar');
    EnvironmentUI.attachTo('#environment', {config: this.attr.config});
  });
});
