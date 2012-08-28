/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*global root_url: false Hogan:false */
var Zipkin = Zipkin || {};
Zipkin.Util = (function(Zipkin) {

  var bind = function(target, fn) {
    return function() {
      return fn.apply(target, arguments);
    };
  };

  var shallowCopy = function(arr) {
    var copied = [];
    for(var i = 0; i < arr.length; i++) { copied.push(arr[i]); }
    return copied;
  };

  /**
   * Add a key/value pair to a dictionary whose default value
   * is an empty array
   */
  var defaultDictPush = function(dict, key, value) {
    if (!dict.hasOwnProperty(key)) {
      dict[key] = [];
    }
    dict[key].push(value);
  };

  /**
   * Get the array associated with a key with default of an empty array
   */
  var defaultDictGet = function(dict, key) {
    if (dict.hasOwnProperty(key)) {
      return dict[key];
    } else {
      return [];
    }
  };

  /**
   * Parses the page query params and stuffs them into an object
   */
  var queryParams = function() {
    var arr = [];
    var str = location.search.substring(1);
    str = decodeURIComponent(str);
    $.each(str.split("&"), function(i, pair) {
      var index = pair.indexOf("=");
      if (index !== -1) {
        arr.push([pair.substring(0, index), pair.substring(index+1)]);
      }
    });
    return arr;
  };

  /**
   * Takes a timestamp in milliseconds and returns a string describing
   * the timestamp in relation to now
   */
  var timeAgoInWords = function(timestamp) {
    var mapping = [
      [60, "second"],
      [60, "minute"],
      [24, "hour"],
      [7, "day"],
      [52, "week"],
      [0, "year"]
    ];
    var plural = function(word, value) {
      if (value == 1) {
        return word;
      } else {
        return word + "s";
      }
    };
    var timeUnit = function(value, m) {
      if (m.length == 1) {
        return value[0][1];
      }
      if (value < m[0][0]) {
        return [value, m[0][1]];
      }
      return timeUnit(value / m[0][0], m.slice(1));
    };

    var now = new Date().getTime()
      , delta = Math.abs(now - timestamp) / 1000 // to seconds
      , prefix = "about"
      , timeValue = 1
      , unit = "minute"
      , suffix = "ago"
      ;

    if (now < timestamp) {
      suffix = "in the future";
    }

    var pair = timeUnit(delta, mapping);
    timeValue = pair[0].toFixed(0);
    unit = pair[1];

    return [prefix, timeValue, plural(unit, timeValue), suffix].join(" ");
  };

  /**
   * Convenience method for using Hogan templates. If we have already
   * fetched the template, initiate the callback. Otherwise, fetch it
   * before invoking the callback.
   */
  var templates = {};
  var templatize = function(name, callback) {
    if(templates.hasOwnProperty(name)) {
      callback(templates[name]);
    } else {
      $.ajax({
        type: 'GET',
        url: root_url + 'public/templates/' + name + '.mustache',
        success: function(data){
          templates[name] = Hogan.compile(data);
          callback(templates[name]);
        }
      });
    }
  };

  var TEMPLATES = {
    GET_TRACE          : "get-trace",
    ONEBOX_LEFT        : "onebox-left",
    ONEBOX_RIGHT       : "onebox-right",
    QUERY              : "query",
    QUERY_ADD_ANNOTATION: "query-add-annotation",
    QUERY_ADD_KV       : "query-add-kv",
    SERVICE_TAG        : "service-tag",
    SPAN_DETAILS       : "span-details"
  };

  return {
    bind: bind,
    shallowCopy: shallowCopy,
    defaultDictPush: defaultDictPush,
    defaultDictGet: defaultDictGet,
    queryParams: queryParams,
    timeAgoInWords: timeAgoInWords,
    templatize: templatize,
    TEMPLATES: TEMPLATES
  };
})(Zipkin);
