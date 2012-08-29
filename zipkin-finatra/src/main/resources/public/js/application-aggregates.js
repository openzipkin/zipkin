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
Zipkin.Application.Aggregates = (function() {
  var templatize = Zipkin.Util.templatize
    , TEMPLATES = Zipkin.Util.TEMPLATES
    ;

  var initialize = function () {
    Zipkin.Base.initialize();

    $("[class*=span]").addClass("span-aggregates");
    $(".container").addClass("container-aggregates");

    var href = window.location.href;

    $(".js-zipkin-navbar > li > a").each(function (index, elem) {
      var parent = $(elem).parent();
      if ($(elem).attr("href") == href ) {
        parent.addClass("active");
      } else if (parent.hasClass("active")) {
        parent.removeClass("active");
      }
    });

    var pillSelector = function(name) {
      return $("[id=" + name + "-pill]").parent();
    }

    pillSelector("dependencies").on('click', function(event) {
      pillSelector("service-report").removeClass("active");
      pillSelector("dependencies").addClass("active");
      $(".container").width(1500);
      $("#service-report").hide();
      $("#global-dependency").show();
    });

    pillSelector("service-report").on('click', function(event) {
      pillSelector("dependencies").removeClass("active");
      pillSelector("service-report").addClass("active");
      $(".container").width(1210);
      $("#global-dependency").hide();
      $("#service-report").show();
    });


    $('.date-input').each(function() {
      var self = $(this)
        , self_val = self.val();

      $(this).DatePicker({
        eventName: 'focus',
        format:'m-d-Y',
        date: self_val,
        current: self_val,
        starts: 0,
        onBeforeShow: function(){
          self.DatePickerSetDate(self_val, true);
        },
        onChange: function(formated, dates){
          self.val(formated);
        }
      });
    });

    $(".nav-dropdowns > li > ul > li").each(function(index, elem) {
      $(elem).on('mouseover', function (event) {
        $(".nav-dropdowns > li > ul > li").each(function (i, el) {
          $(el).removeClass("active");
        });
      }).on('click', function(event) {
        $(elem).addClass("active");
        s = $("#global-dependency > div");
        $("#global-dependency").empty();
        $("#global-dependency").append(s);
        getDependencyTree();
      });
    })

    var getDependencyTree = function () {
      // Show some loading stuff while we wait for the query
      $('#help-msg').hide();
      $('#error-box').hide();
      $('#loading-data').show();

      var query_data = {
        adjust_clock_skew: Zipkin.Base.clockSkewState() ? 'true' : 'false',
      };

//   TODO: Use AJAX request to get data

      Zipkin.GetDependencyTree.initialize();
    };
    getDependencyTree();
  };

  return {
    initialize: initialize
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

    var nodes = [{name: "foo"},
                 {name: "bar"},
                 {name: "baz"}];

	var links = [{source: 0, target: 1, value: 0.1, count: 0.1},
	             {source: 0, target: 2, value: 0.1, count: 0.1},
	             {source: 1, target: 2, value: 0.1, count: 0.1},
	             {source: 2, target: 1, value: 0.1, count: 0.1}];

        var dependencyOptions = {
          width: ($(window).width() > Zipkin.Config.MAX_AGG_WINDOW_SIZE ? Zipkin.Config.MAX_AGG_GRAPHIC_WIDTH: Zipkin.Config.MIN_AGG_GRAPHIC_WIDTH) + 200
        };

        try {
          $('#loading-data').hide();
          var globalDependencies = new Zipkin.GlobalDependencies(nodes, links, dependencyOptions);
          globalDependencies.chart;
          $('#service-report').show();
          $('#global-dependency').show();
        } catch (e) {
          console.log(e);
          $('#loading-data').hide();
          $("#error-msg").text("Something went wrong rendering the dependency tree :(");
          $(".error-box").show();
          return;
        }

        var prevWidth = $(window).width();
        $(window).resize(function () {
          var newWidth = $(window).width();
          if (Zipkin.Base.windowResized(prevWidth, newWidth)) {
            var w = newWidth >= Zipkin.Config.MAX_AGG_WINDOW_SIZE ? Zipkin.Config.MAX_AGG_GRAPHIC_WIDTH : Zipkin.Config.MIN_AGG_GRAPHIC_WIDTH;
            globalDependencies.resize(w + 200);
            prevWidth = newWidth;
          }
        });
      }
    };
  })();

  return {
    initialize: initialize
  };
})();