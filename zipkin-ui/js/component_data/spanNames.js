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
import {getError} from '../../js/component_ui/error';
import $ from 'jquery';

export default component(function spanNames() {
  this.updateSpanNames = function(ev, serviceName) {
    if (!serviceName) {
      this.trigger('dataSpanNames', {spans: []});
      return;
    }
    $.ajax(`api/v2/spans?serviceName=${serviceName}`, {
      type: 'GET',
      dataType: 'json'
    }).done(spans => {
      this.trigger('dataSpanNames', {spans: spans.sort()});
    }).fail(e => {
      this.trigger('uiServerError', getError('cannot load span names', e));
    });
  };

  this.after('initialize', function() {
    this.on('uiChangeServiceName', this.updateSpanNames);
    this.on('uiFirstLoadSpanNames', this.updateSpanNames);
  });
});
