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

export default component(function jsonPanel() {
  this.show = function(e, data) {
    this.$node.find('.modal-title').text(data.title);
    this.$node.find('.save').attr('href', data.link);
    this.$node.find('.modal-body pre code').text(JSON.stringify(data.obj, null, 2));
    this.$node.modal('show');
  };
  this.copyToClipboard = function() {
    const traceJson = document.getElementById('trace-json-content');
    function copyListener(e) {
      e.clipboardData.setData('text/plain', traceJson.textContent);
      e.preventDefault();
    }
    document.addEventListener('copy', copyListener);
    document.execCommand('copy');
    document.removeEventListener('copy', copyListener);
  };
  this.after('initialize', function() {
    this.$node.modal('hide');
    this.on(document, 'uiRequestJsonPanel', this.show);
    this.on('#copy2clipboard', 'click', this.copyToClipboard);
  });
});
