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
/* eslint-disable prefer-template */
import {component} from 'flightjs';
import $ from 'jquery';
import queryString from 'query-string';

export default component(function lookback() {
  this.refreshCustomFields = function() {
    if (this.$node.val() === 'custom') {
      $('#custom-lookback').show();
    } else {
      $('#custom-lookback').hide();
    }
  };

  this.render = function() {
    const selectedLookback = queryString.parse(window.location.search).lookback;
    this.$node.find('option').each((i, option) => {
      const $option = $(option);
      if ($option.val() === selectedLookback) {
        $option.prop('selected', true);
      }
    });
  };

  this.after('initialize', function() {
    this.render();
    this.refreshCustomFields();

    this.on('change', this.refreshCustomFields);
  });
});
