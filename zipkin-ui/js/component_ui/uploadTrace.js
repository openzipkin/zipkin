/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {component} from 'flightjs';
import FullPageSpinnerUI from '../component_ui/fullPageSpinner';
import {traceToMustache} from '../component_ui/traceToMustache';
import {treeCorrectedForClockSkew} from '../component_data/skew';

export function ensureV2(trace) {
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
        const raw = JSON.parse(evt.target.result);
        ensureV2(raw);
        const corrected = treeCorrectedForClockSkew(raw);
        const modelview = traceToMustache(corrected);
        model = {modelview, trace: raw};
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
