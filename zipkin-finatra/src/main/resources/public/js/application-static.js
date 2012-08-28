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
/*global root_url:false */
var Zipkin = Zipkin || {};
Zipkin.Application = Zipkin.Application || {};
Zipkin.Application.Static = (function() {

  var hideError = function() {
    $("#input-json-error").hide()
  };

  var showError = function(msg) {
    $("#input-json-error-body").text(msg);
    $("#input-json-error").show()
  };

  var initialize = function() {
    Zipkin.Base.initialize();

    $(window).on("click", ".js-input-json-btn", function(e) {
      hideError();

      var data = $("#input-json").val().trim();
      if (data.length === 0) {
        showError("Empty!");
        return;
      }

      try {
        var obj = $.parseJSON(data);
        Zipkin.Application.Show.getTraceSuccess("")(obj);
      } catch (e) {
        showError("Invalid JSON");
      }
    });
  };

  return {
    initialize: initialize
  };
})();