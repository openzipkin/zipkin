import {component} from 'flightjs';
import FullPageSpinnerUI from '../component_ui/fullPageSpinner';
import traceToMustache from '../../js/component_ui/traceToMustache';
import {mergeV2ById} from '../spanCleaner';
import {SPAN_V1} from '../spanConverter';

// We are about to switch all internals to v2 format. Unsupporting v1 allows easier progress
export function convertV2ToV1(trace) {
  if (!Array.isArray(trace) || trace.length === 0) {
    throw new Error('input is not a list');
  }
  const first = trace[0];
  if (!first.traceId || !first.id) {
    throw new Error('List<Span> implies at least traceId and id fields');
  }
  if (first.binaryAnnotations || (!first.localEndpoint && !first.remoteEndpoint && !first.tags)) {
    throw new Error(
      'v1 format is not supported. For help, contact https://gitter.im/openzipkin/zipkin');
  }
  return SPAN_V1.convertTrace(mergeV2ById(trace));
}

export default component(function uploadTrace() {
  this.doUpload = function() {
    const files = this.node.files;
    if (files.length === 0) {
      return;
    }

    const reader = new FileReader();
    reader.onload = evt => {
      let model;
      try {
        const rawTrace = JSON.parse(evt.target.result);
        const v1Trace = convertV2ToV1(rawTrace);
        const modelview = traceToMustache(v1Trace);
        model = {modelview, trace: rawTrace};
      } catch (e) {
        this.trigger('uiServerError',
              {desc: 'Cannot parse file', message: e});
        throw e;
      } finally {
        this.trigger(document, 'uiHideFullPageSpinner');
      }

      this.trigger(document, 'traceViewerPageModelView', model);
    };

    reader.onerror = evt => {
      this.trigger(document, 'uiHideFullPageSpinner');
      this.trigger('uiServerError',
            {desc: 'Cannot load file', message: `${evt.target.error.name}`});
    };

    this.trigger(document, 'uiShowFullPageSpinner');
    setTimeout(() => reader.readAsText(files[0]), 0);
  };

  this.after('initialize', function() {
    this.on('change', this.doUpload);
    FullPageSpinnerUI.teardownAll();
    FullPageSpinnerUI.attachTo('#fullPageSpinner');
  });
});
