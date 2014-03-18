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
Zipkin.Application.Show = (function() {
  var templatize = Zipkin.Util.templatize
    , TEMPLATES = Zipkin.Util.TEMPLATES
    ;

  var exportTrace = function(traceId, clockSkew) {
    window.open("/api/get/" + traceId + "?adjust_clock_skew=" + clockSkew);
  };

  var getTraceSuccess = function(traceId) {
    return function(data) {
      data.has_filtered_spans = data.trace.spans.length > Zipkin.Config.MIN_SPANS_TO_FILTER;

      /* Data comes in with microsecond timestamp/durations, so we have to sanitize it first */
      data.traceSummary.duration = data.traceSummary.durationMicro / 1000;
      data.traceSummary.startTimestamp /= 1000;
      data.traceSummary.endTimestamp /= 1000;
      data.trace.startTimestamp /= 1000;
      data.trace.endTimestamp /= 1000;
      data.trace.duration /= 1000;

      $.each(data.trace.spans, function(i, span) {
        span.startTimestamp /= 1000;
        span.duration /= 1000;

        $.each(span.annotations, function(j, ann) {
          ann.timestamp /= 1000;
        });
      });

      $.each(data.traceTimeline.annotations, function(i, ann) {
        ann.timestamp /= 1000;
      });

      /* Some fields for the template */
      data.timeAgoInWords = Zipkin.Util.timeAgoInWords(data.trace.startTimestamp);
      var date = new Date(data.trace.startTimestamp);
      data.date = [date.getMonth() + 1, date.getDate(), date.getFullYear()].join("-");
      data.time = [
        date.getHours(), 
        date.getMinutes() < 10 ? "0" + date.getMinutes() : date.getMinutes(), 
        date.getSeconds() < 10 ? "0" + date.getSeconds() : date.getSeconds()].join(":");
      data.duration = data.trace.duration;

      templatize(TEMPLATES.GET_TRACE, function(template) {

        Zipkin.Base.enableClockSkewBtn();

        /* Construct Zipkin.Span, Zipkin.Annotation, and Zipkin.KvAnnotation objects to pass on */
        var spanMap = {}
          , spans = []
          , annotations = []
          , kvAnnotations = []
          , kvAnnotationsMap = {}
          ;

        var traceStartTime = data.trace.startTimestamp;

        data.trace.spans.sort(function(a,b) {
            var startDiff = a.startTimestamp - b.startTimestamp;
            return (startDiff == 0)?a.duration - b.duration:startDiff;
        });

        $.each(data.trace.spans, function(i, span) {
          span.startTime = span.startTimestamp - traceStartTime;
          span.endTime = span.endTimestamp - traceStartTime;

          var s = Zipkin.fromRawSpan(span);
          spanMap[s.id] = s;
          spans.push(s);

          annotations = s.getAnnotations();
          kvAnnotations = s.getKvAnnotations();
        });

        var content = template.render(data);

        $('#loading-data').hide();
        $('#trace-content').html(content);
        $('#trace-content').show();

        /* Bind the export trace data to the handler */
        $(".export-trace-btn").click(function(e) {
          exportTrace(traceId, Zipkin.Base.clockSkewState() ? 'true' : 'false');
        });

        Zipkin.GetTrace.initialize(data.trace, spans, annotations, kvAnnotations);
      });
    };
  };

  var initialize = function (traceId) {
    Zipkin.Base.initialize();

    var getTrace = function () {
      // Show some loading stuff while we wait for the query
      $('#help-msg').hide();
      $('#error-box').hide();
      $('#trace-content').hide();
      $('#loading-data').show();

      var query_data = {
        adjust_clock_skew: Zipkin.Base.clockSkewState() ? 'true' : 'false',
      };

      $.ajax({
        type: 'GET',
        url: "/api/get/" + traceId,
        data: query_data,
        success: getTraceSuccess(traceId),
        error: function(xhr, status, error) {
          $('#trace-content').hide();
          $('#loading-data').hide();
          $('#error-msg').text(error);
          $('.error-box').show();

          Zipkin.Base.enableClockSkewBtn();
        }
      });
    };

    Zipkin.Base.disableClockSkewBtn();
    Zipkin.Base.addClockSkewListener(getTrace);
    getTrace();
  };

  return {
    initialize: initialize,
    getTraceSuccess: getTraceSuccess
  };
})();

Zipkin.GetTrace = (function() {
  var templatize = Zipkin.Util.templatize
    , TEMPLATES = Zipkin.Util.TEMPLATES
    , autocompleteTerms = []
    ;

  var initialize = function(trace, spans, annotations, kvAnnotations) {

    // Is the current trace pinned or not?
    var pinned = false;

    // Make sure the pin button reflects our current pin status
    var updatePinButton = function() {
      if(pinned) {
        $('.pin-trace-btn').attr("value", "true");
        $('.pin-trace-btn').removeClass('btn-info').addClass('btn-danger');
        $('.pin-trace-icon').removeClass('icon-star-empty').addClass('icon-star');
      } else {
        $('.pin-trace-btn').attr("value", "false");
        $('.pin-trace-btn').removeClass('btn-danger').addClass('btn-info');
        $('.pin-trace-icon').removeClass('icon-star').addClass('icon-star-empty');
      }
      $('.pin-trace-div').show();
    };

    // Check if the pin button should be on or off
    var updatePinStatus = function() {
      $.ajax({
        type: 'GET',
        url: '/api/is_pinned/' + trace.traceId,
        success: function(data){
          pinned = data.pinned === true;
          updatePinButton();
        },
        error: function(xhr, status, error) {
          $('#pinned-modal-error').text("Could not check trace pin status due to this error: " + error);
          $('#pinned-modal').modal('show');
        }
      });
    };

    updatePinStatus();

    // Pin button
    $('.pin-trace-btn').click(function (e) {
      $.ajax({
        type: 'GET',
        url: '/api/pin/' + trace.traceId + '/' + !pinned, // toggled the pinned status
        success: function(data){
          pinned = data.pinned === true;
          updatePinButton();
        },
        error: function(xhr, status, error) {
          $('#pinned-modal-error').text("Could not pin trace due to this error: " + error);
          $('#pinned-modal').modal('show');
        }
      });
      return false;
    });

    $('.pin-trace-btn').on('hover', function(e) {
      $('.pin-trace-tooltip').tooltip({placement: "top"});
    });

    // Hook up trace nav buttons
    $(".trace-nav > ul > li").each(function (index, elem) {
      $(elem).on('click', function (event) {
        if (!$(elem).hasClass("active")) {
          $(".trace-nav > ul > li").each(function (i, e) {
            $(e).removeClass("active");
          });
          $(elem).addClass("active");
        }
        var hash = event.target.hash;

        $(".trace-nav > ul > li > a").each(function (i, e) {
          if (e.hash != hash) {
            $(e.hash).hide();
          }
        });
        $(hash).show();
        $("html body").animate({scrollTop: 0}, "slow");
      });
    });

    $(".trace-timestamp-label").tooltip({ placement: 'left' });

    Trace._init(trace, spans, annotations, kvAnnotations);
  };

  var Trace = (function Trace() {

    return {
      _init: function(trace, spans, annotations, kvAnnotations) {
        var lazyTree = new Zipkin.LazyTree(trace.trace_id, trace.duration);
        var traceSummary;

        $.each(spans, function(spanId, span) {
          autocompleteTerms.push(span.getServiceName());
          lazyTree.addNode(span);
        });

        /* Uniqify */
        autocompleteTerms = (function() {
          var terms = {};
          $.each(autocompleteTerms, function(i, t) {
            terms[t] = 1;
          });
          return Object.keys(terms);
        })();
        autocompleteTerms.sort();

        var tree = lazyTree.build();

        var filterDurationPredicate = function(span) {
          return span.getDuration() < Zipkin.Config.FILTER_SPAN_DURATION_THRESHOLD;
        };

        var filterMap = function(predicate, markUnfiltered) {
          return function(node) {
            if (predicate(node)) {
              var children = node.getChildren();
              var allFiltered = true;
              $.each(children, function(i, child) {
                allFiltered = allFiltered && (child.isFilter !== undefined && child.isFilter());
              });

              if (allFiltered) {
                return Zipkin.makeFilterSpan(node);
              }
            } else if (markUnfiltered) {
              console.log(node);
              node.pinned = true;
            }
            return node;
          };
        };

        var aggregateFiltered = function(children) {
          var newChildren = []
            , filtered = []
            ;

          var pushFiltered = function(nodes) {
            var unwrapped = []
              ;
            $.each(nodes, function(i, node) {
              unwrapped = unwrapped.concat(node.getSpans());
            });

            var grouped = Zipkin.groupFiltered(unwrapped);
            $.each(grouped, function(i, group) {
              newChildren.push(Zipkin.squashFiltered(group));
            });
          };

          for(var i=0; i<children.length; i++) {
            var child = children[i];
            if (child.isFilter && child.isFilter()) {
              filtered.push(child);
            } else {
              if (filtered.length > 0) {
                pushFiltered(filtered);
                filtered.length = 0;
              }
              newChildren.push(child);
            }
          }

          if (filtered.length > 0) {
            pushFiltered(filtered);
          }
          return newChildren;
        };

        var unwrapSingles = function(node) {
          if (node.isFilter && node.isFilter()) {
            var spans = node.getSpans();
            if (spans.length === 1) {
              return spans[0];
            }
          }
          return node;
        };

        /* TraceSummary */
        var traceSummaryTree = tree.clone();
        if (spans.length > Zipkin.Config.MIN_SPANS_TO_FILTER) {
          traceSummaryTree = traceSummaryTree
            .map(filterMap(filterDurationPredicate))
            .reduceChildren(aggregateFiltered);
        }

        /* TraceDependency */
        var linkMap = {};
        var addLink = function(source, target, duration, depth) {
          var key = [source, target];
          if (linkMap.hasOwnProperty(key)) {
            var obj = linkMap[key];
            obj.duration += duration;
            obj.depth = Math.max(obj.depth, depth);
            obj.count += 1;
          } else {
            linkMap[key] = {
              source: source,
              target: target,
              duration: duration,
              depth: depth,
              count: 1
            };
          }
        };
        var dependencyMapper = function(node) {
          var parent = node.getParent();
          if (parent) {
            addLink(parent.getServiceName(), node.getServiceName(), node.duration, node.depth);
          }
        };
        tree.clone().map(dependencyMapper);

        var links = [];
        $.each(linkMap, function(k, v) {
          if (v.source != v.target) {
            links.push(v);
          }
        });

        var dependencyList = { links: links, root: tree.getRoot().getServiceName() };

        /* Onebox */
        Zipkin.Onebox.initialize(tree.clone());

        // we can't fit the whole service name, so let's cut it short
        var shortenString = function(str, maxLength) {
          if (str.length > maxLength) {
            return str.substring(0, maxLength) + "..";
          } else {
            return str;
          }
        };

        var hoverCallback = function(span) {
          //spanDetails(span);
        };

        var spanDetails = function(span) {
          var title = span.getServiceName() + ": " + span.getDuration().toFixed(3) + "ms";
          var start = trace.startTimestamp;

          var anns = $.map(span.getAnnotations(), function (a) {
            return {
              startTime: (a.timestamp - start).toFixed(1),
              service: a.getSpan().getServiceName(),
              name: a.getSpan().getName(),
              annotation: a.getValue(),
              hostAddress: a.getHostAddress(),
              host: a.getHost()
            };
          });

          // we want breadcrumbs so we easily can figure out the parents of the current span
          var currSpan = span;
          var breadcrumbs = [currSpan];
          currSpan.last = true;
          currSpan.shortServices = shortenString(currSpan.getServices(), 15);
          while (currSpan.getParent()) {
            currSpan = currSpan.getParent();
            currSpan.last = false;
            currSpan.shortServices = shortenString(currSpan.getServices(), 15);
            breadcrumbs.push(currSpan);
          }
          breadcrumbs.reverse();

          var kvAs = span.getKvAnnotations()
            ;

          $.each(kvAs, function(i, item) {
            if (item.getAnnotationType() == 1) {
              item.isBinary = true;
            }
          });

          var data = {
            annotations: anns,
            breadcrumbs: breadcrumbs,
            kv: kvAs,
            hasKvAnnotations: kvAs.length > 0,
            title: title
          };

          templatize(TEMPLATES.SPAN_DETAILS, function(template) {
            var content = template.render(data);
            $(".trace-desc-body").html(content);
          });

          $(".hiddenKvAnnotation").click(function(e) {
            var el = $(e.target);
            var data = el.attr("data");
            var s = $("<span>").html(data);
            el.replaceWith(s);
          });
        };

        var clickCallback = function(span) {
          spanDetails(span);
          $(".details-close").show();
          $("#annotation-modal").modal();
        };

        $("#overview").on("click", ".details-close", function() {
          $(".details-close").hide();
        });

        var summaryOptions = {
          hoverCallback: hoverCallback,
          clickCallback: clickCallback,
          chart: {
            width: $(window).width() > Zipkin.Config.MAX_WINDOW_SIZE ? Zipkin.Config.MAX_GRAPHIC_WIDTH: Zipkin.Config.MIN_GRAPHIC_WIDTH
          }
        };

        var dependencyOptions = {
          width: ($(window).width() > Zipkin.Config.MAX_WINDOW_SIZE ? Zipkin.Config.MAX_GRAPHIC_WIDTH: Zipkin.Config.MIN_GRAPHIC_WIDTH) + 200
        };

        var traceSummaryTreeList = traceSummaryTree.toList();

        /* Unwrap all the filtered nodes with only one child */
        traceSummaryTreeList = $.map(traceSummaryTreeList, function(elem) {
          if (elem.isFilter && elem.isFilter()) {
            var spans = elem.getSpans();
            if (spans.length == 1) {
              return spans[0];
            }
          }
          return elem;
        });

        try {
          traceSummary = new Zipkin.TraceSummary(traceSummaryTreeList, traceSummaryTree.getRoot(), trace.duration, summaryOptions);
          traceSummary.render();

          var traceDependencies = new Zipkin.TraceDependencies(dependencyList, dependencyOptions);
        } catch (e) {
          console.log(e);
          $("#trace-content").hide();
          $("#error-msg").text("Something went wrong rendering this trace :\\");
          $(".error-box").show();
          return;
        }

        $('table').delegate('.expandable-annotation', 'click', function(e) {
          $(this).next().toggle();
          $(this).next().find('div').slideDown();
        });

        $('.span-summary').delegate('.close-modal', 'click', function(e) {
          e.preventDefault();
          $('.span-summary').hide();
        });

        /* Bind hotkeys */
        $(document).keyup(function (e) {
          var focus = $(".js-trace-search-field").is(":focus");
          if (e.keyCode === 191) {
            /* '/' key: Focus on search box */
            if (!focus) {
              $(".js-trace-search-field").focus();
              $(".js-trace-search-field").select();
            }
          } else if (e.keyCode === 27) {
            /* Esc key: Clear search box */
            if (focus) {
              $(".js-trace-search-field").val("");
              $(".js-trace-search-form").submit();
            }
          }
        });

        /* Bind autocomplete terms */
        $(".js-trace-search-field").autocomplete({source: autocompleteTerms, autoFocus: true});

        $(".js-trace-search-form").submit(function(e) {
          /* Blur the field */
          $(".js-trace-search-field").blur();

          var value = $.trim($(".js-trace-search-field").val());

          /* Re-render the graphic to filter by the search term */
          var predicate, markUnfiltered;
          if (value === "") {
            predicate = filterDurationPredicate;
            markUnfiltered = false;
          } else {
            predicate = function(node) {
              return node.getServiceName().indexOf(value) == -1;
            };
            markUnfiltered = true;
          }

          var filteredTree =
            tree.clone()
              .map(filterMap(predicate, markUnfiltered))
              .reduceChildren(aggregateFiltered);

          traceSummary.updateTree(filteredTree.toList());

          return false;
        });

        $("#overview-pill").click(function(e) {
          $(".js-trace-search-form").show();
        });

        var hideSearchForm = function(e) { $(".js-trace-search-form").hide(); };
        $("#timeline-pill").click(hideSearchForm);
        $("#dependencies-pill").click(hideSearchForm);

        // Bind button for expanding all filtered spans
        $('.expand-all').click(function (event) {
          traceSummary.expandAll();
          $(event.target).hide();
          $('.collapse-all').show();
        });

        // Bind button for collapsing all uncollapsed filtered spans
        $('.collapse-all').click(function (event) {
          traceSummary.collapseAll();
          $(event.target).hide();
          $('.expand-all').show();
        });

        /* Bind click handlers for span detail breadcrumbs */
        var breadcrumbClick = function(event) {
          var spanId = event.srcElement.id
            , span = lazyTree.nodes[spanId]
            ;

          spanDetails(span);
          $("#annotation-modal").modal();
        };
        $("#overview").on("click", ".breadcrumb li a", breadcrumbClick);
        $("#annotation-modal").on("click", ".breadcrumb li a", breadcrumbClick);

        (function () {
          var prevWidth = $(window).width();
          $(window).resize(function () {
            var newWidth = $(window).width();
            if (Zipkin.Base.windowResized(prevWidth, newWidth)) {
              var w = newWidth > Zipkin.Config.MAX_WINDOW_SIZE ? Zipkin.Config.MAX_GRAPHIC_WIDTH : Zipkin.Config.MIN_GRAPHIC_WIDTH;
              traceSummary.resize(w);
              traceDependencies.resize(w + 200);
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