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

export default component(function remoteServiceNames() {
  this.updateRemoteServiceNames = function(ev, serviceName) {
    if (!serviceName) {
      this.trigger('dataRemoteServiceNames', {remoteServices: []});
      return;
    }
    $.ajax(`api/v2/remoteServices?serviceName=${serviceName}`, {
      type: 'GET',
      dataType: 'json'
    }).done(remoteServices => {
      this.trigger('dataRemoteServiceNames', {remoteServices: remoteServices.sort()});
    }).fail(e => {
      if (e.status && e.status === 404) { // remote service names is a new endpoint
        this.trigger('dataRemoteServiceNames', {remoteServices: []});
        return;
      }
      this.trigger('uiServerError', getError('cannot load remote service names', e));
    });
  };

  this.after('initialize', function() {
    this.on('uiChangeServiceName', this.updateRemoteServiceNames);
    this.on('uiFirstLoadRemoteServiceNames', this.updateRemoteServiceNames);
  });
});
