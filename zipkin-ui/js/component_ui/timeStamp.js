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
import bootstrapDatepicker from 'bootstrap-datepicker'; // eslint-disable-line no-unused-vars

export default component(function timeStamp() {
  this.init = function() {
    this.$timestamp = this.$node.find('.timestamp-value');
    this.$date = this.$node.find('.date-input');
    this.$time = this.$node.find('.time-input');
    const ts = this.$timestamp.val();
    this.setDateTime((ts) ? moment(Number(ts)) : moment());
  };

  this.setDateTime = function(time) {
    this.$date.val(time.format('YYYY-MM-DD'));
    this.$time.val(time.format('HH:mm'));
  };

  this.setTimestamp = function(time) {
    this.$timestamp.val(time.valueOf());
  };

  this.dateChanged = function(e) {
    const time = moment(e.date);
    time.add(moment.duration(this.$time.val()));
    this.setTimestamp(moment.utc(time));
  };

  this.timeChanged = function() {
    const time = moment(this.$date.val(), 'YYYY-MM-DD');
    time.add(moment.duration(this.$time.val()));
    this.setTimestamp(moment.utc(time));
  };

  this.after('initialize', function() {
    this.init();
    this.on(this.$time, 'change', this.timeChanged);
    this.$date.datepicker({format: 'yyyy-mm-dd'});
    this.on(this.$date, 'changeDate', this.dateChanged.bind(this));
  });
});
