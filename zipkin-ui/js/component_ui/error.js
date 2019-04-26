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
import $ from 'jquery';

export default component(function ErrorUI() {
  this.after('initialize', function() {
    this.on(document, 'uiServerError', function(evt, e) {
      this.$node.append($('<div></div>').text(`ERROR: ${e.desc}: ${e.message}`));
      this.$node.show();
    });
  });
});

// converts an jqXhr error to a string
export function errToStr(e) {
  return e.responseJSON ? e.responseJSON.message : `server error (${e.statusText})`;
}

// transforms an ajax error into something that is passed to
// trigger('uiServerError')
export function getError(desc, e) {
  return {
    desc,
    message: errToStr(e)
  };
}
