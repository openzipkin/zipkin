import {component} from 'flight';
import $ from 'jquery';
import EnvironmentUI from '../component_ui/environment';

const CommonUI = component(function Common() {
  this.after('initialize', function() {
    const tmpl = require('../../../templates/v2/layout.mustache');
    this.$node.html(tmpl());
    EnvironmentUI.attachTo('#environment');
  });
});

export default CommonUI;
