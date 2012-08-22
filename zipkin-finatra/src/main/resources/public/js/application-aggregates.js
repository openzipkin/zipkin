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
//= require zipkin
var Zipkin = Zipkin || {};
Zipkin.Application = Zipkin.Application || {};
Zipkin.Application.Aggregates = (function() {
  var templatize = Zipkin.Util.templatize
    , TEMPLATES = Zipkin.Util.TEMPLATES
    ;

  var initialize = function () {
    Zipkin.Base.initialize();

    var getDependencyTree = function () {
      // Show some loading stuff while we wait for the query
      $('#help-msg').hide();
      $('#error-box').hide();
      $('#loading-data').show();

      var query_data = {
        adjust_clock_skew: Zipkin.Base.clockSkewState() ? 'true' : 'false',
      };

//      $.ajax({
//        type: 'GET',
//        url: root_url + "aggregates/dependency_tree",
////        url: root_url + "api/get/" + traceId,
//        data: query_data,
//        success: getTraceSuccess(traceId),
////        success: getTraceSuccess(traceId),
//        error: function(xhr, status, error) {
//          $('#trace-content').hide();
//          $('#loading-data').hide();
//          $('#error-msg').text(error);
//          $('.error-box').show();
//
//        }
      Zipkin.GetDependencyTree.initialize();
    };
    getDependencyTree();
  };

  return {
    initialize: initialize
//    getTraceSuccess: getTraceSuccess
  };
})();

Zipkin.GetDependencyTree = (function() {
  var templatize = Zipkin.Util.templatize
    , TEMPLATES = Zipkin.Util.TEMPLATES
    , autocompleteTerms = []
    ;

  var initialize = function() {
    DependencyTree._init();
  };

  var DependencyTree = (function DependencyTree() {

    return {
      _init: function() {

	var nodes = [];
	var links = [];



/*        var links = [];
        $.each(linkList, function(k, v) {
            links.push(v);
        });*/

        //var dependencyList = { links: links, root: "planet" };

        var dependencyOptions = {
          width: ($(window).width() > Zipkin.Config.MAX_AGG_WINDOW_SIZE ? Zipkin.Config.MAX_AGG_GRAPHIC_WIDTH: Zipkin.Config.MIN_AGG_GRAPHIC_WIDTH) + 200
        };

        try {
          $('#loading-data').hide();
          var globalDependencies = new Zipkin.GlobalDependencies(nodes, links, dependencyOptions);
          globalDependencies.chart;
//          $('#global-dependency').html(globalDependencies.chart());
          $('#global-dependency').show();
        } catch (e) {
          console.log(e);
          $("#error-msg").text("Something went wrong rendering the dependency tree :(");
          $(".error-box").show();
          return;
        }

        (function () {
          var prevWidth = $(window).width();
          $(window).resize(function () {
            var newWidth = $(window).width();
            if (Zipkin.Base.windowResized(prevWidth, newWidth)) {
              var w = newWidth >= Zipkin.Config.MAX_AGG_WINDOW_SIZE ? Zipkin.Config.MAX_AGG_GRAPHIC_WIDTH : Zipkin.Config.MIN_AGG_GRAPHIC_WIDTH;
              globalDependencies.resize(w + 200);
              prevWidth = newWidth;
            }
           });
        })();
      }
    };
  })();

  return {
    initialize: initialize
  };
})();