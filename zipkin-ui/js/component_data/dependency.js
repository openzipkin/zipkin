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
import moment from 'moment';
import $ from 'jquery';

export default component(function dependency() {
  let services = {};
  let dependencies = {};

  this.getDependency = function(endTs, lookback) {
    let url = `api/v2/dependencies?endTs=${endTs}`;
    if (lookback) {
      url += `&lookback=${lookback}`;
    }
    $.ajax(url, {
      type: 'GET',
      dataType: 'json',
      success: links => {
        this.links = links.sort((a, b) => a.parent - b.parent || a.child - b.child);
        this.buildServiceData(links);
        this.trigger('dependencyDataReceived', links);
      },
      failure: (jqXHR, status, err) => {
        const error = {
          message: `Couldn't get dependency data from backend: ${err}`
        };
        this.trigger('dependencyDataFailed', error);
      }
    });
  };

  this.buildServiceData = function(links) {
    services = {};
    dependencies = {};
    links.forEach(link => {
      const {parent, child} = link;

      dependencies[parent] = dependencies[parent] || {};
      dependencies[parent][child] = link;

      services[parent] = services[parent] || {serviceName: parent, uses: [], usedBy: []};
      services[child] = services[child] || {serviceName: child, uses: [], usedBy: []};

      services[parent].uses.push(child);
      services[child].usedBy.push(parent);
    });
  };

  this.after('initialize', function() {
    this.on(document, 'dependencyDataRequested', function(event, {endTs, lookback}) {
      this.getDependency(endTs, lookback);
    });

    this.on(document, 'serviceDataRequested', function(event, {serviceName}) {
      this.getServiceData(serviceName, data => {
        this.trigger(document, 'serviceDataReceived', data);
      });
    });

    this.on(document, 'parentChildDataRequested', function(event, {parent, child}) {
      this.getDependencyData(parent, child, data => {
        this.trigger(document, 'parentChildDataReceived', data);
      });
    });

    const endTs = document.getElementById('endTs').value || moment().valueOf();
    const startTs = document.getElementById('startTs').value;
    let lookback;
    if (startTs && endTs > startTs) {
      lookback = endTs - startTs;
    }
    this.getDependency(endTs, lookback);
  });

  this.getServiceData = function(serviceName, callback) {
    callback(services[serviceName]);
  };

  this.getDependencyData = function(parent, child, callback) {
    callback(dependencies[parent][child]);
  };
});
