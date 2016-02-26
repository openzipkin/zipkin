import {component} from 'flight';
import $ from 'jquery';
import queryString from 'query-string';
import chai from 'chai';
import {traceSummary, traceSummariesToMustache} from '../component_ui/traceSummary'

export const DefaultData = component(function defaultData() {
  function verifyTraceToMustacheTransformation(modelviewJs) {
    $.ajax('/modelview' + window.location.pathname + window.location.search, {
      type: "GET",
      dataType: "json",
      context: this,
      success: (modelviewScala) => {
        chai.config.truncateThreshold = 0;
        console.log('Comparing Scala output and JavaScript output:');
        chai.expect(modelviewJs.traces).to.deep.equal(modelviewScala.traces);
        console.log('Success: The Scala-transformed results and JavaScript-transformed results are equal.');
      }
    });
  }

  this.after('initialize', function() {
    const serviceName = queryString.parse(window.location.search).serviceName;
    if (serviceName) {
      $.ajax('/api/v1/traces' + window.location.search, {
        type: 'GET',
        dataType: 'json',
        context: this,
        success: (traces) => {
          const modelview = {traces: traceSummariesToMustache(serviceName, traces.map(traceSummary)) };
          this.trigger('defaultPageModelView', modelview);
          if (serviceName) {
            verifyTraceToMustacheTransformation(modelview);
          }
        }
      });
    } else {
      this.trigger('defaultPageModelView', {traces: []});
    }
  });
});
