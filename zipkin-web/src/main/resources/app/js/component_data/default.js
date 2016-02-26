import {component} from 'flight';
import $ from 'jquery';
import queryString from 'query-string';
import chai from 'chai';
import shallowDeepAlmostEqual from 'chai-shallow-deep-almost-equal';
import {traceSummary, traceSummariesToMustache} from '../component_ui/traceSummary'
chai.use(shallowDeepAlmostEqual);

export const DefaultData = component(function defaultData() {
  function verifyTraceToMustacheTransformation(modelviewJs) {
    $.ajax('/modelview' + window.location.pathname + window.location.search, {
      type: "GET",
      dataType: "json",
      context: this,
      success: (modelviewScala) => {
        console.log('Comparing Scala output and JavaScript output:');
        chai.expect(modelviewJs.traces).to.shallowDeepAlmostEqual(modelviewScala.traces);
        chai.expect(modelviewScala.traces).to.shallowDeepAlmostEqual(modelviewJs.traces);
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
