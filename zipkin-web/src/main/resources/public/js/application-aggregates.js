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
      } else {
        parent.removeClass("active");
      }
    });

    var radial = new Zipkin.RadialDependencies;
    radial.render();
  };

  return {
    initialize: initialize
  };
})();

