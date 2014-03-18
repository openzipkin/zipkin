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

/*
 * Onebox: important information synthesized from the trace tree
 */
Zipkin.Onebox = (function() {

  var searchKeys = Zipkin.Config.ONEBOX.KEYS;
  var duplicateKeyValues = [];
  var serviceSummary = [];

  var treeMungers = [
    /* Find duplicate key/value annotations */
    function(tree) {
      tree.map(function(node) {
        var children = node.getChildren();
        var keyToCount = {};

        $.each(children, function(i, child) {
          var annotations = child.getKvAnnotations();
          $.each(annotations, function(j, annotation) {
            var key = annotation.getKey()
              , value = annotation.getValue()
              ;

            $.each(searchKeys, function(sKey) {
              if (sKey === key) {
                var newKey = key + value;
                if (keyToCount.hasOwnProperty(newKey)) {
                  var obj = keyToCount[newKey];
                  obj.count += 1;

                  var name = annotation.getSpan().getName();
                  if (obj.spans.hasOwnProperty(name)) {
                    obj.spans[name] += 1;
                  } else {
                    obj.spans[name] = 1;
                  }
                } else {
                  var spans = {};
                  spans[annotation.getSpan().getName()] = 1;
                  keyToCount[newKey] = {
                    key: key,
                    value: value,
                    spans: spans,
                    count: 1
                  };
                }
              }
            });
          });
        });

        $.each(keyToCount, function(k, v) {
          if (v.count > 1) {
            var obj = keyToCount[k];
            duplicateKeyValues.push({
              key: obj.key,
              value: obj.value,
              parentSpan: node,
              count: obj.count,
              spans: $.map(obj.spans, function(count, name) { return {name: name, count: count}; })
            });
          }
        });

        /* Return the node since we don't want to change anything */
        return node;
      });
    },

    /* Service summary */
    function(tree) {
      var serviceData = {};

      var walkTree = function(node) {
        var duration = node.getDuration()
          , children = node.getChildren()
          , serviceName = node.getServiceName()
          , waitTime = 0
          , childData = []
          ;

        $.each(children, function(i, child) {
          walkTree(child);
          childData.push([child.getStartTime(), child.getId(), "start"]);
          childData.push([child.getEndTime(), child.getId(), "end"]);
        });

        childData.sort(function(a,b) { return a[0] - b[0]; });
        var set = {};
        for(var i=0; i<childData.length; i++) {
          var item = childData[i];
          if (item[2] === "start") {
            set[item[1]] = item[0];
          } else {
            var start = set[item[1]];
            if (start !== undefined) {
              delete set[item[1]];
              if (Object.keys(set).length === 0) {
                /* None left, so we can record this duration */
                waitTime += item[0] - start;
              }
            }
          }
        }

        if (serviceData.hasOwnProperty(serviceName)) {
          serviceData[serviceName].cpuTime += duration - waitTime;
          serviceData[serviceName].waitTime += waitTime;
        } else {
          serviceData[serviceName] = {
            cpuTime: duration - waitTime,
            waitTime: waitTime
          };
        }
      };
      walkTree(tree.getRoot());

      $.each(serviceData, function(service, data) {
        serviceSummary.push({
          service: service,
          cpuTime: data.cpuTime.toFixed(3),
          waitTime: data.waitTime.toFixed(3)
        });
      });
    }
  ];

  var mungeTree = function(tree) {
    $.each(treeMungers, function(i, m) {
      m(tree);
    });
  };

  var mungeSpans = function(spans) {

  };

  var mungeAnnotations = function(annotations) {

  };

  var mungeKvAnnotations = function(kvAnnotations) {
  };

  var initialize = function(tree, spans, annotations, kvAnnotations) {
    mungeTree(tree);
    mungeSpans(spans);
    mungeAnnotations(annotations);
    mungeKvAnnotations(kvAnnotations);

    var onebox = $(".onebox");

    /* Populate onebox left */
    Zipkin.Util.templatize(Zipkin.Util.TEMPLATES.ONEBOX_LEFT, function(template) {

      duplicateKeyValues.sort(function(a,b) { return b.count-a.count; });

      var content = template.render({
        hasDuplicates: duplicateKeyValues.length > 0,
        duplicates: duplicateKeyValues,
        duplicateCount: duplicateKeyValues.length
      });
      $(".onebox-left").append(content);
    });

    /* Populate onebox right */
    Zipkin.Util.templatize(Zipkin.Util.TEMPLATES.ONEBOX_RIGHT, function(template) {
      serviceSummary.sort(function(a,b) { return b.cpuTime - a.cpuTime; });
      var content = template.render({
        serviceSummary: serviceSummary
      });
      $(".onebox-right").append(content);
    });

    /* Bind click handler for all sections */
    $(".onebox-section").on("click", ".onebox-section-title", function(event) {
      var target = $(event.target);
      target.parents(".onebox-section").children(".onebox-section-body").slideToggle();
      target.children("i").toggleClass("icon-plus").toggleClass("icon-minus");
    });

    /* Bind tooltips */
    $(".onebox-tooltip").tooltip();
  };

  return {
    initialize: initialize
  };
})();
