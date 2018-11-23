import {component} from 'flightjs';
import $ from 'jquery';
import {getError} from '../../js/component_ui/error';
import traceToMustache from '../../js/component_ui/traceToMustache';
import {correctForClockSkew} from '../skew';

export function toContextualLogsUrl(logsUrl, traceId) {
  if (logsUrl) {
    return logsUrl.replace('{traceId}', traceId);
  }
  return logsUrl;
}

// Converts the response into data for trace.mustache. Missing required data will raise an error.
export function convertSuccessResponse(rawResponse, logsUrl) {
  const corrected = correctForClockSkew(rawResponse);
  const modelview = traceToMustache(corrected, logsUrl);
  return {modelview, trace: rawResponse};
}

export default component(function TraceData() {
  this.after('initialize', function() {
    const traceId = this.attr.traceId;
    const logsUrl = toContextualLogsUrl(this.attr.logsUrl, traceId);
    $.ajax(`api/v2/trace/${traceId}`, {
      type: 'GET',
      dataType: 'json'
    }).done(raw => {
      this.trigger('tracePageModelView', convertSuccessResponse(raw, logsUrl));
    }).fail(e => {
      this.trigger('uiServerError', getError(`Cannot load trace ${traceId}`, e));
    });
  });
});
