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
      getDependencyTree();
    };
  };

  return {
    initialize: initialize,
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
      _init: (function() {
        var lazyTree = new Zipkin.LazyTree(0);

        var Node = function(server, city, id, parentId, depth) {
            this.server = server;
            this.city = city;
            this.id = id;
            this.parentId = parentId;
            this.depth = depth;
            this.children = [];
            this.parent = null;

            this.getId = function() {
                return this.id;
            };

            this.getParentId = function() {
                return this.parentId;
            };

            this.addChild = function(node) {
                this.children.push(node);
            };

            this.setParent = function(p) {
                this.parent = p;
            };

            this.getParent = function() {
                return this.parent;
            };

            this.setDepth = function(depth) {
                this.depth = depth;
            };

            this.getChildren = function() {
                return this.children;
            };

            this.setChildren = function(children) {
                this.children = children;
            }

            this.clone = function() {
                n = new Node(this.server, this.client, this.id, this.parentId);
                n.setParent(this.parent);
                n.setDepth(this.depth);
                n.children = this.children;
                return n;
            };

        }

        /* TraceDependency */
        var linkMap = [
            new Node("city", "state", 6, 4, 3),
            new Node("town", "state", 5, 4, 3),
            new Node("state", "country", 4, 2),
            new Node("province", "country", 3, 2, 2),
            new Node("country", "continent", 2, 1, 1),
            new Node("continent", "planet", 1, 0, 0)
        ];

        $.each(linkMap, function(index, entry) {
            lazyTree.addNode(entry)
        })

        var tree = lazyTree.build();

        var addLink = function(source, target, depth) {
          var key = [source, target];
          if (linkMap.hasOwnProperty(key)) {
            var obj = linkMap[key];
            obj.depth = Math.max(obj.depth, depth);
            obj.count += 1;
          } else {
            linkMap[key] = {
              source: source,
              target: target,
              depth: depth,
              count: 1
            };
          }
        };

        var dependencyMapper = function(node) {
          var parent = node.getParent();
          if (node.getParent()) {
            addLink(node.client, node.server, node.depth);
          }
        };

        tree.clone().map(dependencyMapper);

        var links = [];
        $.each(linkMap, function(k, v) {
          if (v.source != v.target) {
            links.push(v);
          }
        });

        var dependencyList = { links: links, root: tree.getRoot().client() };

        var dependencyOptions = {
          width: ($(window).width() > Zipkin.Config.MAX_WINDOW_SIZE ? Zipkin.Config.MAX_GRAPHIC_WIDTH: Zipkin.Config.MIN_GRAPHIC_WIDTH) + 200
        };

        try {
//          traceSummary = new Zipkin.TraceSummary(traceSummaryTreeList, traceSummaryTree.getRoot(), trace.duration, summaryOptions);
//          traceSummary.render();

          var traceDependencies = new Zipkin.TraceDependencies(dependencyList, dependencyOptions);
          traceDependencies.render();
        } catch (e) {
          console.log(e);
          $("#error-msg").text("Something went wrong rendering this trace :\\");
          $(".error-box").show();
          return;
        }

        (function () {
          var prevWidth = $(window).width();
          $(window).resize(function () {
            var newWidth = $(window).width();
            if (Zipkin.Base.windowResized(prevWidth, newWidth)) {
              var w = newWidth > Zipkin.Config.MAX_WINDOW_SIZE ? Zipkin.Config.MAX_GRAPHIC_WIDTH : Zipkin.Config.MIN_GRAPHIC_WIDTH;
              traceDependencies.resize(w + 200);
              prevWidth = newWidth;
            }
           });
        })();
      })()
    };
  })();

  return {
    initialize: initialize
  };
})();