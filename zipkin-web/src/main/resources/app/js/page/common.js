import {component} from 'flight';
import $ from 'jquery';
import EnvironmentUI from '../component_ui/environment';
import NavbarUI from '../component_ui/navbar';

const CommonUI = component(function Common() {
  this.after('initialize', function() {
    const tmpl = require('../../../templates/v2/layout.mustache');
    this.$node.html(tmpl());
    NavbarUI.attachTo('#navbar');
    EnvironmentUI.attachTo('#environment');
  });
});

export default CommonUI;
