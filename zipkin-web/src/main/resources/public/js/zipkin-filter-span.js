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
var Zipkin = Zipkin || {};
Zipkin.FilterSpan = (function(superClass) {

  var FilterSpan = function(config) {
    Zipkin.Util.bind(this, superClass.prototype.constructor)(config);

    this.filter    = config.filter;
    this.collapsed = config.collapsed;
    this.spans     = config.spans || [];
  };

  jQuery.extend(FilterSpan.prototype, superClass.prototype);

  FilterSpan.prototype.isFilter    = function() { return this.filter; };
  FilterSpan.prototype.isCollapsed = function() { return this.collapsed; };
  FilterSpan.prototype.getSpans    = function() { return Zipkin.Util.shallowCopy(this.spans); };
  FilterSpan.prototype.getId       = function() { return Zipkin.Util.bind(this, superClass.prototype.getId)() + "-filtered"; };

  FilterSpan.prototype.setFilter    = function(f) { this.filter = f; };
  FilterSpan.prototype.setCollapsed = function(c) { this.collapsed = c; };
  FilterSpan.prototype.setSpans     = function(s) { this.spans = s; };

  FilterSpan.prototype.getAll = function() {
    return jQuery.extend(
      Zipkin.Util.bind(this, superClass.prototype.getAll)(),
      {
        filter    : this.isFilter(),
        collapsed : this.isCollapsed(),
        spans     : this.getSpans()
      }
    );
  };

  FilterSpan.prototype.toList = function() {
    if (this.isFilter()) {
      /* Bubble all subtree children up to spans of this node */
      var output = []
        , unexplored = Zipkin.Util.shallowCopy(this.spans);

      while (unexplored.length > 0) {
        var s = unexplored.shift();
        if (s.isFilter !== undefined && s.isFilter()) {
          unexplored = s.getSpans().concat(unexplored);
        } else {
          unexplored = s.getChildren().concat(unexplored);
          output.push(s);
        }

      }
      this.spans = output;

      return [this];
    } else {
      return superClass.prototype.toList();
    }
  };

  return FilterSpan;
})(Zipkin.Span);

Zipkin.makeFilterSpan = function(span) {
  return new Zipkin.FilterSpan(
    jQuery.extend(
      span.getAll(),
      { filter: true, collapsed: true, spans: [span] }
    )
  );
};

/* Aggregates a list of filtered nodes into a single filtered nodes whose
   `span` field is the list of filtered nodes. Assumes the input nodes
   have already been unwrapped */
Zipkin.squashFiltered = function(nodes) {
  var unwrapped = []
    , config = {}
    ;
  var squashSpan = function(s) {
    config.startTime = Math.min(config.startTime, s.getStartTime());
    config.endTime   = Math.max(config.endTime, s.getEndTime());
  };

  config.id = nodes[0].getId();
  config.parent = nodes[0].getParent();
  config.startTime = nodes[0].getStartTime();
  config.endTime = nodes[0].getEndTime();
  config.depth = nodes[0].getDepth();

  $.each(nodes, function(i, w) {
    squashSpan(w);
  });

  config.duration = config.endTime - config.startTime;
  config.filter = true;
  config.collapsed = true;
  config.spans = nodes;

  return new Zipkin.FilterSpan(config);
};

Zipkin.groupFiltered = function(nodes) {
  /* Computes a weighted average of the nodes by duration */
  var loss = function(nodeSet) {
    if (nodeSet.length === 0) {
      return 0;
    }
    var num = 0,
        denom = 0,
        sum = 0;
    $.each(nodeSet, function(i, n) {
      var middle = (n.getEndTime() + n.getStartTime()) / 2;
      var duration = n.getDuration();

      num += middle * duration;
      denom += duration;
    });
    var weightedAvg = num / denom;
    $.each(nodeSet, function(i, n) {
      sum = n.duration * Math.abs(weightedAvg - ((n.getEndTime() + n.getStartTime()) / 2));
    });
    return sum;
  };

  if (nodes && nodes.length <= 1) {
    return [nodes];
  }

  /* Use a heuristic to get a subset of all possible splits to
     investigate since looking at all subsets is NP */
  var splits = [];
  for(var i=0; i<nodes.length-1; i += 1) {
    var gap = nodes[i+1].getStartTime() - nodes[i].getEndTime();
    if (gap > 5) {
      splits.push([gap, i]);
    }
  }

  /* Look at the splits in descending gap size */
  splits.sort();

  /* Assumes possibleSplits is sorted in ascending order */
  var splitNodes = function(possibleSplits, start, end) {
    var s = start || 0,
        e = end || nodes.length;
    if (s < e-1 && possibleSplits.length !== 0) {
      var lossAll = loss(nodes.slice(s, e));
      for (var i=possibleSplits.length-1; i>=0; i -= 1) {
        var splitIndex = possibleSplits[i][1];
        if (s <= splitIndex && splitIndex < e) {
          var delta = nodes[splitIndex + 1].getStartTime() - nodes[splitIndex].getEndTime();
          var slice1 = nodes.slice(s, splitIndex+1);
          var slice2 = nodes.slice(splitIndex+1);
          var loss1 = loss(slice1);
          var loss2 = loss(slice2);
          if (loss1 + loss2 < lossAll * delta) {
            var newSplits = possibleSplits.slice(0, i);
            return splitNodes(newSplits, s, splitIndex+1).concat(splitNodes(newSplits, splitIndex+1, e));
          }
        }
      }
    }
    return [nodes.slice(s, e)];
  };
  return splitNodes(splits);
};
