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

/*global d3:false */

//= require d3-2.9.1

//= require zipkin
//= require zipkin-node

var Zipkin = Zipkin || {};
Zipkin.TraceSummary = (function(Zipkin) {

  var redrawHelper = function(chart, d) {
    var id = d.getId();
    var selected = isSelected(d);
    chart.select('#bar-id-' + id).classed('selected', selected);
    chart.select('#bar-text-id-' + id).classed('selected', selected);
    chart.select('#span-labels-id-' + id).classed('selected', selected);
    chart.select('#span-labels-text-id-' + id)
      .text(function (d) {
        if (d.filter) {
          var spans = d.getSpans();
          if (d.collapsed) {
            if (d.selected) {
              return "SHOW: " + spans.length + " filtered spans";
            } else {
              return "COLLAPSED: " + spans.length + " spans";
            }
          } else {
            return "HIDE: " + spans.length + " filtered spans";
          }
        } else {
          return d.getServiceName().toUpperCase().substr(0, 20);
        }
      });
  };

  /*
    These are callback functions, reused often. They should be
    bound to an instance of TraceSummary. <3 JS.
   */
  var hoverEvent = function(d) {
    var chart = d3.select(this.chartSelector);
    d.selected = true;
    redrawHelper(chart, d);
    this.hoverCallback(d);
  };

  var blurEvent = function(d) {
    var chart = d3.select(this.chartSelector);
    d.selected = false;
    redrawHelper(chart, d);
  };

  var clickEvent = function(d, i) {
    var chart = d3.select(this.chartSelector);
    if(d.filter) {
      var data = this.data;
      if (d.collapsed) {
        this.data = data.slice(0, d.index + 1).concat(d.getSpans(), data.slice(d.index + 1));
      } else {
        this.data = data.slice(0, d.index + 1).concat(data.slice(d.index + d.getSpans().length + 1));
      }
      d.collapsed = !d.collapsed;
      this.render();
    } else {
      // pass this to the function, because of "this"
      // confusion at the user-level API
      this.clickCallback(d);
      redrawHelper(chart, d);
    }
  };

  var annotationEvent = function(method) {
    return function(d) { Zipkin.Util.bind(this, method)(d.getSpan()) };
  };

  /**
   * Function to help d3 identify new nodes from old ones
   */
  var keyFunction = function (d) { return d.getId(); };

  var isFiltered = function (d) { return d.isFilter && d.isFilter(); };
  var isSelected = function (d) { return d.selected; };

  var TraceSummary = function(data, root, duration, options) {
    this.duration = duration;
    this.data = data;
    this.root = root;
    $.each(['bar', 'chart'], function(i, val) {
      options[val] = options[val] || {};
    });
    this.chartClass     = "." + (options.chart.className || "trace-summary");
    this.chartSelector  = "g";

    this.hoverCallback  = options.hoverCallback;
    this.clickCallback  = options.clickCallback;

    this.colorClasses   = ["depth0", "depth1", "depth2", "depth3", "depth4", "depth5"];
    this.parentSelector = options.parentSelector || 'body';
    this.barHeight      = options.bar.height || 20;
    this.chartWidth     = options.chart.width  || 720;
    this.chartHeight    = options.chart.height;
    this.chart = this.initChart();
  };

  TraceSummary.prototype.initChart = function() {
    var chart = d3.select(this.chartClass).append('svg')
      .attr('class', this.chartSelector)
      .attr('width', this.chartWidth + 200)
      .append("g")
      .attr('transform', 'translate(180,15)');

    return chart;
  };

  TraceSummary.prototype.updateTree = function(data) {
    this.data = data;
    this.render();
  };

  /**
   * Expands all filtered spans
   */
  TraceSummary.prototype.expandAll = function () {
    var context = this;
    $.each(this.data, function (i, e) {
      if (e.filter && e.collapsed) {
        Zipkin.Util.bind(context, clickEvent)(e, null);
      }
    });
  };

  /**
   * Collapses all expanded filtered spans
   */
  TraceSummary.prototype.collapseAll = function () {
    var context = this;
    $.each(this.data, function (i, e) {
      if (e.filter && !e.collapsed) {
        Zipkin.Util.bind(context, clickEvent)(e, null);
      }
    });
  };

  TraceSummary.prototype.resize = function(chartWidth) {
    this.chartWidth = chartWidth;
    d3.select(this.chartClass)
      .select("svg")
      .attr('width', this.chartWidth + 200)
        .select("g")
        .attr('width', this.chartWidth + 200);
    this.render(null, true);
  };

  TraceSummary.prototype.render = function(data, refresh) {
    data = data || this.data;

    this.drawChartDetail(data, refresh);
    this.drawSpans(data);
    this.drawSpanLabels(data);
    this.drawHierarchy(data);
    this.drawAnnotations(data);
  };

  TraceSummary.prototype.redrawSpan = function(span) {
    var chart = d3.select(this.chartSelector);
    redrawHelper(chart, span);
  };

  /**
   * The draw* methods below are responsible for drawing graphical representation of the
   * trace using d3. We use the convention shown in the d3 documentation as follows for
   * each component:
   *
   * 1. Update: existing elements are selected and updated as necessary
   *
   *    var elements = d3.selectAll(...)
   *      .data(data, keyFunction);
   *
   * 2. Enter: new elements (whose keys were not in the previous data set) are created
   *
   *    elements.enter().append(...)
   *      .attr(...)
   *      ...;
   *
   * 3. Exit: elements whose keys are no longer in the data set are destroyed
   *
   *    elements.exit().remove();
   */

  TraceSummary.prototype.drawSpans = function(data) {
    var x = this.x
      , y = this.y
      , that = this
      ;

    /* Modifier functions for bars */
    var barX = function(d, i) { return x(d.getStartTime()) || 0; };
    var barY = function(d, i) { d.index = i; return y(i) + y.rangeBand() / 2; };
    var barWidth = function(d) {
      if(d.isFilter && d.isFilter()) {
        return x(d.getDuration());
      }

      if (d.getDuration() === 0) {
        return 0;
      }
      if(d.getDuration() > 1) {
        return x(d.getDuration());
      } else {
        return x(1);
      }
    };

    /* Update */
    var bars = d3.select(this.chartSelector)
      .selectAll('.bars')
      .data(data, keyFunction)
      .attr("x", barX)
      .attr("y", barY)
      .attr("width", barWidth)
      .classed("filtered", isFiltered);

    /* Enter */
    var barEnter = bars.enter()
      .append("rect")
      .attr('id', function(d) { return 'bar-id-' + d.getId(); })
      .classed("bars", true)
      .on("mouseover", Zipkin.Util.bind(this, hoverEvent))
      .on("mouseout", Zipkin.Util.bind(this, blurEvent))
      .on("click", Zipkin.Util.bind(this, clickEvent))
      .attr("width", barWidth)
      .attr("height", function(d) { return y.rangeBand() - 1; })
      .attr("x", barX)
      .attr("y", barY)
      .classed("filtered", isFiltered);

    $.each(this.colorClasses, function (i, e) {
      barEnter.classed(e, function (d) { return !d.filter && (d.getDepth() % that.colorClasses.length) == i; });
    });

    /* Exit */
    bars.exit().remove();

    /* Modifier functions for bar text */
    var barTextX = function(d, i) { return (x(d.getStartTime()) || 0) + 8; };
    var barTextY = function(d, i) { return y(i) + y.rangeBand(); };

    /* Update */
    var barText = d3.select(this.chartSelector)
      .selectAll('.bar-text')
      .data(data, keyFunction)
      .attr("x", barTextX)
      .attr("y", barTextY);

    /* Enter */
    barText.enter()
      .append("text")
      .attr('id', function(d) { return 'bar-text-id-' + d.getId(); })
      .attr('class', 'bar-text')
      .attr("dx", -3)
      .attr("dy", ".35em")
      .on("mouseover", Zipkin.Util.bind(this, hoverEvent))
      .on("mouseout", Zipkin.Util.bind(this, blurEvent))
      .on("click", Zipkin.Util.bind(this, clickEvent))
      .text(function(d) {
        if (d.getDuration() === 0) {
          return "";
        }
        var text = d.getDuration().toFixed(3);
        if (!d.isFilter || !d.isFilter()) {
          text += " " + d.getName();
        }
        return text;
      })
      .attr("x", barTextX)
      .attr("y", barTextY);

    /* Exit */
    barText.exit().remove();
  };

  TraceSummary.prototype.drawSpanLabels = function(data, refresh) {
    var x = this.x, y = this.y
    ,   that   = this;

    var chart = d3.select(this.chartSelector);

    /* Modifier functions for span labels */
    var spanLabelX = function(d) { return -170 + (d.getDepth() * 5); };
    var spanLabelY = function(d, i) { return y(i) + y.rangeBand() / 2; };

    /* Update */
    var spanLabels = chart.selectAll('.span-labels')
      .data(data, keyFunction)
      .attr("x", spanLabelX)
      .attr("y", spanLabelY);

    /* Enter */
    spanLabels.enter()
      .append("rect")
      .attr('id', function(d) { return 'span-labels-id-' + d.getId(); })
      .classed("span-labels", true)
      .attr("dx", -180)
      .attr("height", y.rangeBand())
      .on("mouseover", Zipkin.Util.bind(this, hoverEvent))
      .on("mouseout", Zipkin.Util.bind(this, blurEvent))
      .on("click", Zipkin.Util.bind(this, clickEvent))
      .attr("width", function(d) { return 165 - (d.getDepth() * 5); })
      .attr("height", y.rangeBand())
      .attr("x", spanLabelX)
      .attr("y", spanLabelY)
      .classed("selected", isSelected);

    /* Exit */
    spanLabels.exit().remove();

    /* Modifier Functions for span label text */
    var textsY = function(d, i) { return that.y(i) + that.y.rangeBand() + 5; };
    var textsText = function (d) {
      if(d.filter) {
        if (d.isCollapsed()) {
          if (d.selected) {
            return "SHOW: " + d.spans.length + " collapsed spans";
          } else {
            return "COLLAPSED: " + d.spans.length + " spans";
          }
        } else {
          return "HIDE: " + d.spans.length + " collapsed spans";
        }
      }
      return d.getServiceName().toUpperCase().substr(0, 20);
    };

    /* Update */
    var texts = d3.select(this.chartSelector)
      .selectAll(".span-labels-text")
      .data(data, keyFunction)
      .attr("y", textsY)
      .text(textsText);

    /* Enter */
    texts.enter()
      .append("text")
      .attr('id', function(d) { return 'span-labels-text-id-' + d.getId(); })
      .attr("class", "span-labels-text")
      .attr("x", 0)
      .on("mouseover", Zipkin.Util.bind(this, hoverEvent))
      .on("mouseout", Zipkin.Util.bind(this, blurEvent))
      .on("click", Zipkin.Util.bind(this, clickEvent))
      .attr("dx", function(d) { return -160 + ((d.getDepth() - 1) * 5); })
      .attr("y", textsY)
      .text(textsText);

    /* Exit */
    texts.exit().remove();
  };

  TraceSummary.prototype.drawHierarchy = function(data) {
    var x = this.x
      , y = this.y
      , that = this
      ;
    /**
     * Since we are iterating through the data sequentially, we can compute the index of the
     * lowest child to draw the vertical line to.
     *
     * Also save the _nodesWithChildren_ since we only want to draw vertical liens down from
     * those.
     */
    var nodesWithChildren = [];
    var idToLowestChild = {};
    for (var i = 0; i < data.length; i += 1) {
      var d = data[i];
      if (d.filter) {
        d.parent = d.spans[0].parent;
      }
      if (d.getParent() && (!idToLowestChild.hasOwnProperty(d.getParent().getId()) || idToLowestChild[d.parent.id] < i)) {
        idToLowestChild[d.parent.id] = i;
      }
      d.index = i;
      if (!d.filter && d.children && d.children.length !== 0) {
        nodesWithChildren.push(d);
      }
    }

    /**
     * Draw hierarchy circles
     */
    var hierarchyCx = function(d) { return -168 + ((d.depth - 1) * 5); };
    var hierarchyCy = function(d, i) { return y(d.index) + y.rangeBand(); };

    /* Update */
    var hierarchy = d3.select(this.chartSelector)
      .selectAll(".span-hierarchy")
      .data(data, keyFunction)
      .attr("cx", hierarchyCx)
      .attr("cy", hierarchyCy);

    /* Enter */
    hierarchy.enter()
      .append("circle")
      .attr("class", 'span-hierarchy')
      .attr("r", 2)
      .on("mouseover", Zipkin.Util.bind(this, hoverEvent))
      .on("mouseout", Zipkin.Util.bind(this, blurEvent))
      .on("click", Zipkin.Util.bind(this, clickEvent))
      .attr("cx", hierarchyCx)
      .attr("cy", hierarchyCy);

    /* Exit */
    hierarchy.exit().remove();

    /**
     * Draw horizontal lines in hierarchy
     */
    var horizX1 = function(d) { if (d === that.root) { return 0; } else { return -173 + ((d.depth - 1) * 5); }};
    var horizX2 = function(d) { if (d === that.root) { return 0; } else { return -170 + ((d.depth - 1) * 5); }};
    var horizY = function(d, i) { return y(d.index) + y.rangeBand(); };

    /* Update */
    var hierarchyLinesHoriz = d3.select(this.chartSelector)
      .selectAll(".span-hierarchy-lines-horiz")
      .data(data, keyFunction)
      .attr("x1", horizX1)
      .attr("x2", horizX2)
      .attr("y1", horizY)
      .attr("y2", horizY);

    /* Enter */
    hierarchyLinesHoriz.enter()
      .append("line")
      .attr("class", "span-hierarchy-lines-horiz")
      .on("mouseover", Zipkin.Util.bind(this, hoverEvent))
      .on("mouseout", Zipkin.Util.bind(this, blurEvent))
      .on("click", Zipkin.Util.bind(this, clickEvent))
      .attr("x1", horizX1)
      .attr("x2", horizX2)
      .attr("y1", horizY)
      .attr("y2", horizY);

    /* Exit */
    hierarchyLinesHoriz.exit().remove();

    /**
     * Draw vertical lines in hierarchy
     */
    var vertX = function(d) { return -168 + ((d.getDepth() - 1) * 5); };
    var vertY1 = function(d, i) { return y(d.index) + y.rangeBand() + 1; };
    var vertY2 = function(d, i) {
      if (idToLowestChild.hasOwnProperty(d.getId())) {
        return y(idToLowestChild[d.getId()]) + y.rangeBand();
      } else {
        return y(d.index) + y.rangeBand();
      }
    };

    /* Update */
    var hierarchyLinesVert = d3.select(this.chartSelector)
      .selectAll(".span-hierarchy-lines-vert")
      .data(nodesWithChildren, keyFunction)
      .attr("y1", vertY1)
      .attr("y2", vertY2);

    /* Enter */
    hierarchyLinesVert.enter()
      .append("line")
      .attr("class", "span-hierarchy-lines-vert")
      .on("mouseover", Zipkin.Util.bind(this, hoverEvent))
      .on("mouseout", Zipkin.Util.bind(this, blurEvent))
      .on("click", Zipkin.Util.bind(this, clickEvent))
      .attr("x1", vertX)
      .attr("x2", vertX)
      .attr("y1", vertY1)
      .attr("y2", vertY2);

    /* Exit */
    hierarchyLinesVert.exit().remove();
  };

  TraceSummary.prototype.drawAnnotations = function(data, refresh) {
    // transform data to array of annotations
    var annotation_data = [];
    var annotation_to_row = {};
    var min = Number.MAX_VALUE;
    $.each(data, function (i, d) {
      var anns = d.getAnnotations();
      $.each(anns, function (j, a) {
        if (a.getTimestamp() < min) {
          min = a.getTimestamp();
        }
        if ($.inArray(a.getValue(), ["Client send", "Client receive", "Server send", "Server receive"]) == -1) {
          a.services = d.services;
          a.annotations = d.annotations;
          annotation_data.push(a);
          annotation_to_row[a.getValue() + a.getTimestamp()] = i;
        }
      });
    });
    var x = this.x, y = this.y;

    var annotationsCx = function(d) { return x((d.getTimestamp()-min)/1000); };
    var annotationsCy = function(d, i) { return y(annotation_to_row[d.getValue() + d.getTimestamp()]) + y.rangeBand(); };

    /* Update */
    var annotations = d3.select(this.chartSelector)
      .selectAll(".annotations")
      .data(annotation_data)
      .attr("cx", annotationsCx)
      .attr("cy", annotationsCy);

    /* Enter */
    annotations.enter()
      .append("circle")
      .attr("class", "annotations")
      .attr("r", 4)
      .on("mouseover", Zipkin.Util.bind(this, annotationEvent(hoverEvent)))
      .on("mouseout", Zipkin.Util.bind(this, annotationEvent(blurEvent)))
      .on("click", Zipkin.Util.bind(this, annotationEvent(clickEvent)))
      .attr("cx", annotationsCx)
      .attr("cy", annotationsCy)
      .attr("rel", "tooltip")
      .attr("title", function(annotation) { return annotation.getValue(); });

    $(".annotations").tooltip();

    /* Exit */
    annotations.exit().remove();
  };

  TraceSummary.prototype.setHeight = function(height) {
    // still trying to figure out why I can't just do this.chart.attr('height', ...)
    d3.selectAll('svg').attr('height', height);
  };

  TraceSummary.prototype.drawChartDetail = function(data, refresh) {
    var chartHeight = data.length * this.barHeight;

    var ys = [];
    for(var i = 0; i < data.length; i += 1) { ys.push(i); }

    var x = this.x = d3.scale.linear().domain([0, this.duration]).range([0, this.chartWidth]);
    var y = this.y = d3.scale.ordinal().domain(ys).rangeBands([0, chartHeight]);

    var chart = d3.select('svg').attr('height', chartHeight + 50);

    var lines = d3.select(this.chartSelector).selectAll(".guideline")
      .data(x.ticks(10))
      .attr("x1", x)
      .attr("x2", x);

    if (refresh || lines.empty()) {
      lines.enter()
        .append("line")
        .attr("class", "guideline")
        .attr("x1", x)
        .attr("x2", x)
        .attr("y1", 0)
        .attr("y2", chartHeight + 20);
    } else {
      lines.transition().attr('y2', chartHeight + 20);
    }

    lines.exit().remove();

    var rule = d3.select('g').selectAll(".rule")
      .data(x.ticks(10))
      .attr("x", x);

    rule.enter()
      .append("text")
      .attr("class", "rule")
      .attr("y", 0)
      .attr("dy", -3)
      .attr("x", x)
      .text(function (d) { return d + "ms"; });

    rule.exit().remove();
  };

  return TraceSummary;
})(Zipkin);
