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
Zipkin.Application.Index = (function() {

  var ORDER_DURATION_DESC = 0
    , ORDER_DURATION_ASC = 1
    , ORDER_TIMESTAMP_DESC = 2
    , ORDER_TIMESTAMP_ASC = 3
    ;

  var templatize = Zipkin.Util.templatize
    , TEMPLATES = Zipkin.Util.TEMPLATES
    ;

  /* Data retrieved for a particular query */
  var traceData;

  var useQueryParams = false;
  var searchQuery = "";

  var filter_submit;

  /**
   * Views
   */
  var AllSelectView = Zipkin.Application.Views.SelectView.extend({
    render: function() {
      var view = new this.optionView({model: new Zipkin.Application.Models.Span({name: "all"})});
      this.$el.append(view.render().el)
      AllSelectView.__super__.render.apply(this);
    }
  });

  var SpanOptionView = Zipkin.Application.Views.CookiedOptionView.extend({
    cookieName: "lastSpanName"
  });

  /*
   * @param collection: Zipkin.Application.Models.SpanList
   */
  var SpanSelectView = AllSelectView.extend({
    id: "spanName",
    attributes: {
      name: "spanName"
    },
    optionView: SpanOptionView,

    initialize: function() {
      this.collection.bind("all", this.render, this);
    }
  });

  /*
   * @param model: Zipkin.Application.Models.Service
   */
  var ServiceOptionView = Zipkin.Application.Views.CookiedOptionView.extend({
    cookieName: "lastServiceName",
    className: "service-select"
  });

  /*
   * @param collection: Zipkin.Application.Models.ServiceList,
   */
  var ServiceSelectView = Zipkin.Application.Views.SelectView.extend({
    id: "serviceName",
    attributes: {
      name: "serviceName"
    },
    optionView: ServiceOptionView,

    events: {
      "add": "renderAndChange",
      "change": "serviceNameChange",
      "reset": "renderAndChange"
    },

    initialize: function() {
      ServiceSelectView.__super__.initialize.apply(this);
      this.collection.bind("all", this.renderAndChange, this);
    },

    renderAndChange: function() {
      this.render();
      this.serviceNameChange();
    },

    serviceNameChange: function() {
      var selected = this.$el.children(":selected").val();
      this.spanList = new Zipkin.Application.Models.SpanList([], {
        serviceName: selected
      });

      this.spanList.fetch();
      this.spanSelectView = new SpanSelectView({
        collection: this.spanList
      });

      // FIXME this is really ugly, need to figure out a more Backboney way to do this
      /* Fetch top annotations for this service */
      $.ajax({
        type: 'GET',
        url: '/api/top_annotations?serviceName=' + selected,
        success: function(data) {
          if (data.length > 0) {
            $("#time_annotation").autocomplete({source: data});
          }
        }
      });

      /* Fetch top key value annotations for this service */
      $.ajax({
        type: 'GET',
        url: '/api/top_kv_annotations?serviceName=' + selected,
        success: function(data) {
          if (data.length > 0) {
            $("#annotation_key").autocomplete({source: data});
          }
        }
      });
    }

  });

  var QueryResultsView = Backbone.View.extend({
    initialize: function() {
      this.collection.bind("all", this.render, this);
    },

    render: function() {
      var data = this.collection.toJSON();

      // FIXME more backbony
      var parsed = parseQueryResults(data);
      var traces = parsed.data
      var serviceName = $("#serviceName option:selected").val();
      addServiceTag(serviceName);

      traces = updateFilteredServices(traces);
      sortQueryResults(traces);

      templatize(TEMPLATES.QUERY, function(template) {
        var context = { traces: traces };
        var content = template.render(context);
        $('#loading-data').hide();
        refreshQueryResults(content);
        Zipkin.Base.enableClockSkewBtn();
      });

      updateFilterCurrentCount(traces.length);
      updateFilterTotalCount(traces.length);
      updateFilterDuration(parsed.minStartTime, parsed.maxStartTime);

      $('#help-msg').hide();
      $('#error-box').hide();
      $(".infobar").show();
      $(".service-tag-list").show();
      return this;
    }
  });

  /*
   * @param serviceList: Zipkin.Application.Models.ServiceList
   */
  var IndexView = Backbone.View.extend({
    el: $("#index-view"),

    initialize: function() {
      var that = this;
      this.serviceList = this.options.serviceList;
      this.serviceSelectView = new ServiceSelectView({
        collection: this.serviceList
      });
      this.serviceSelectView.on("change", function() {
        this.spanSelectView.trigger("change");
      });
      if (this.serviceList.length === 0) {
        this.serviceList.fetch({
          success: function() {
            that.render();
          }
        });
      } else {
        this.serviceSelectView.renderAndChange();
      }
    },

    render: function() {
      this.serviceSelectView.render();
      return this;
    }
  });

  var parseQueryResults = function(queryResults) {
    var minStartTime = Number.MAX_VALUE
      , maxStartTime = Number.MIN_VALUE
      , maxDuration  = Number.MIN_VALUE
      ;

    /* Find the longest one */
    $.each(queryResults, function(i, d) {
      maxDuration = Math.max(d.durationMicro / 1000, maxDuration);
    });

    var parsed = $.map(queryResults, function(e) {
      minStartTime = minStartTime < e.startTimestamp ? minStartTime : e.startTimestamp;
      maxStartTime = maxStartTime > e.startTimestamp ? maxStartTime : e.startTimestamp;

      e.duration = e.durationMicro / 1000;
      e.width = (e.duration / maxDuration) * 100;
      e.serviceCounts = $.map(e.serviceCounts, function(count, key) {
        return { name: key, count: count };
      });
      e.url = "/traces/" + e.traceId;
      e.startTime = Zipkin.Util.timeAgoInWords(e.startTimestamp / 1000);
      return e;
    });

    return {
      data: parsed,
      minStartTime: minStartTime,
      maxStartTime: maxStartTime,
      maxDuration: maxDuration
    };
  };

  /* Adds a service tag to the service tag list */
  var addServiceTag = function(serviceName, closeable) {
    if ($("span[id*='service-tag-" + serviceName + "']").length === 0) {
      templatize(TEMPLATES.SERVICE_TAG, function(template) {
        var context = { name : serviceName, closeable: closeable };
        var content = template.render(context);
        $(".service-tags").append(content);
      });
    }
  };

  /* Gets the services that are current in the service tag list */
  var getFilteredServices = function () {
    var services = {};
    $(".service-tag").each(function (i, e) {
      services[$(e).text()] = 1;
    });
    return services;
  };

  var getSortOrder = function() {
    return $(".js-sort-order").val();
  };

  var sortQueryResults = function(data) {
    /* Option index directly maps to the correct sort order */
    var sortOrder = getSortOrder();

    data.sort(function(a, b) {
      if (sortOrder == ORDER_TIMESTAMP_ASC) {
        return new Date(a.startTimestamp) - new Date(b.startTimestamp);
      } else if (sortOrder == ORDER_TIMESTAMP_DESC) {
        return new Date(b.startTimestamp) - new Date(a.startTimestamp);
      } else if (sortOrder == ORDER_DURATION_ASC) {
        return a.duration - b.duration;
      } else {
        /* ORDER_DURATION_DESC */
        return b.duration - a.duration;
      }
    });
  };

  /* Change the label colors of query results to reflect those that are filtered */
  var updateFilteredServices = function (traces) {
    var services = getFilteredServices();
    return $.map(traces, function(t) {
      $.each(t.serviceCounts, function (i, s) {
        if (services && services.hasOwnProperty(s[0])) {
          s.labelColor = "service-tag-filtered";
        } else {
          s.labelColor = "";
        }
      });
      return t;
    });
  };

  /* Plug in the new data */
  var refreshQueryResults = function (content) {
    $('#query-results').hide();
    $('#query-results').html(content);
    $('#query-results').show();
  };

  /* Update the counter for number of traces displayed on the page */
  var updateFilterCurrentCount = function(n) {
    $(".filter-current").text(n);
  };

  /* Update the counter for number of traces we have data for */
  var updateFilterTotalCount = function(n) {
    $(".filter-total").text(n);
  };

  var updateFilterDuration = function(minStartStr, maxStartStr) {
    var min = new Date(minStartStr)
      , max = new Date(maxStartStr)
      , delta = max.getTime() - min.getTime()
      , suffix
      ;


    if (delta < 1000) {
      suffix = "ms";
    } else {
      delta = delta / 1000;
      if (delta < 60) {
        suffix = "seconds";
      } else {
        delta = delta / 60;
        suffix = "minutes";
      }
    }

    $(".filter-duration").text(delta.toFixed(3) + " " + suffix);
  };

  var initialize = function(services, queryResults) {

    /* Filter the query results based on service tag list */
    var filterQueryResults = function (services) {
      if (!services) {
        return traceData;
      }
      return $.grep(Zipkin.Util.shallowCopy(traceData), function (e, i) { // why the hell are these inverted
        var satisfied = true;
        $.each(services, function (key, value) {
          var service_satisfied = false;
          $.each(e.serviceCounts, function (i, serviceObj) {
            if (key == serviceObj.name) {
              service_satisfied = true;
            }
          });
          satisfied = satisfied && service_satisfied;
        });
        return satisfied;
      });
    };

    /* Click handler for adding a service filter */
    var labelClick = function (event) {
      event.stopPropagation();
      var target = $(event.target);
      var serviceName = target.attr("value");
      addServiceTag(serviceName, true);

      var services = getFilteredServices();
      var newData = updateFilteredServices(filterQueryResults(services));
      templatize(TEMPLATES.QUERY, function(template) {
        var context = { traces: newData };
        var content = template.render(context);
        refreshQueryResults(content);

        updateFilterCurrentCount(newData.length);
      });
      return false;
    };

    /* Click handler for removing a service filter */
    var labelRemove = function (event) {
      $('#query-results').hide();
      var target = $(event.target);
      var serviceName = target.attr('id').slice("service-tag-close-".length);

      $("li[id*='service-tag-li-" + serviceName + "']").remove();

      var services = getFilteredServices();
      var newData = updateFilteredServices(filterQueryResults(services));
      templatize(TEMPLATES.QUERY, function(template) {
        var context = { traces: newData };
        var content = template.render(context);
        refreshQueryResults(content);

        updateFilterCurrentCount(newData.length);
      });
      return false;
    };

    /* Bind click handlers */
    $("#query-results").on("click", ".traces .service-tag-label", labelClick);
    $(".service-tags").on("click", "li span.service-tag-close", labelRemove);

    /* Search for traces */
    filter_submit = function(adjust_clock_skew) {

      // Show some loading stuff while we wait for the query
      $('#help-msg').hide();
      $('#error-box').hide();
      $(".infobar").hide();
      $('#query-results').hide();

      var spanName = $('select[name=spanName]').val();

      if (spanName == "") {
        $('#error-box').text("Invalid query: no span name").show();
        return false;
      }

      var baseParams = {
        serviceName: $('select[name=serviceName]').val(),
        spanName: spanName,
        endDatetime: $('input[name=end_date]').val() + " " + $('input[name=end_time]').val(),
        limit: $('input[name=limit]').val()
      }

      var query = new Zipkin.Application.Models.Query(baseParams);

      $(".additional-filter-group > .js-time-annotation > div > .controls > input").each(function(i, elem) {
        query.addTimeAnnotation($(elem).val());
      });

      $(".additional-filter-group > .js-kv-annotation").each(function(i, elem) {
        query.addKeyValueAnnotation(
          $("input[name=annotation_key]", elem).val(),
          $("input[name=annotation_value]", elem).val()
        );
      });

      Zipkin.Base.setCookie("lastServiceName", baseParams.serviceName);

      $('#loading-data').show();

      var queryResults = query.execute();
      queryResultsView = new QueryResultsView({collection: queryResults});

      /* Update the URL to reflect the search */
      searchQuery = queryResults.url().split("?")[1];
      history.pushState({}, "Zipkin", "/?" + searchQuery);

      return false;
    };

    // Clicks on the lookup button
    $('.filter-submit button').click(filter_submit);

    /**
     * Fix index components for wider windows
     */
    var prevWidth = $(window).width();
    var resize = function (newWidth) {
      if (newWidth > Zipkin.Config.MAX_WINDOW_SIZE) {
        $(".main-content").addClass("main-content-wide");
        //$(".sidebar").removeClass("span3").addClass("span2");
      } else {
        $(".main-content").removeClass("main-content-wide");
        //$(".sidebar").removeClass("span2").addClass("span3");
      }
    };
    $(window).resize(function () {
      var newWidth = $(window).width();
      if (Zipkin.Base.windowResized(prevWidth, newWidth)) {
        resize(newWidth);
        prevWidth = newWidth;
      }
    });
    resize(prevWidth);

    // Register filter_submit to refire when clockskew is toggled
    Zipkin.Base.addClockSkewListener(filter_submit);

    $('.date-input').each(function() {

      var self = $(this)
        , self_val = self.val();

      $(this).DatePicker({
        eventName: 'focus',
        format:'m-d-Y',
        date: self_val,
        current: self_val,
        starts: 0,
        // calendars: 2,
        // mode: "range",
        onBeforeShow: function(){
          self.DatePickerSetDate(self_val, true);
        },
        onChange: function(formated, dates){
          self.val(formated);
          // self.DatePickerHide();
        }
      }).blur(function(){
        // $(this).DatePickerHide();
      });
    });

    $(".js-sort-order").change(function (e) {
      var services = getFilteredServices();
      var newData = updateFilteredServices(filterQueryResults(services));
      sortQueryResults(newData);

      templatize(TEMPLATES.QUERY, function(template) {
        var context = { traces: newData };
        var content = template.render(context);
        refreshQueryResults(content);
      });
    });

    /* Bind click handler for additional annotation/kv filters */
    $(".js-add-annotation-filter").click(function(e) {
      e.stopPropagation();
      templatize(TEMPLATES.QUERY_ADD_ANNOTATION, function(template) {
        var content = template.render({});
        $(".additional-filter-group").append(content);
        $(".additional-filter-group > .hide").slideDown('fast');
        $(".additional-filter-group > .control-group:last-child > .controls > input").focus();
      });

      return false;
    });
    $(".js-add-kv-filter").click(function(e) {
      templatize(TEMPLATES.QUERY_ADD_KV, function(template) {
        var content = template.render({});
        $(".additional-filter-group").append(content);
        $(".additional-filter-group > .hide").slideDown('fast');
        $(".additional-filter-group > .control-group:last-child > .controls:nth-child(2) > input").focus();
      });
      return false;
    });

    /* Bind click handler for removing additional annotation/kv filter */
    $(".additional-filter-group").on("click", ".control-group > .remove > i", function(e) {
      e.stopPropagation();
      $(e.target).parents(".control-group").remove();
      return false;
    });

    Zipkin.Base.initialize();

    var params = Zipkin.Util.queryParams();
    if (params.length > 0) {
      useQueryParams = params;
    }

    var serviceList = new Zipkin.Application.Models.ServiceList(services);
    var indexView = new IndexView({
      serviceList: serviceList
    });

    if (queryResults !== undefined && queryResults.length > 0) {
      var parsed = parseQueryResults(queryResults);
      var traces = parsed.data
      var serviceName = $("#serviceName option:selected").val();
      addServiceTag(serviceName);

      traces = updateFilteredServices(traces);
      sortQueryResults(traces);

      templatize(TEMPLATES.QUERY, function(template) {
        var context = { traces: traces };
        var content = template.render(context);
        $('#loading-data').hide();
        refreshQueryResults(content);
        Zipkin.Base.enableClockSkewBtn();
      });

      updateFilterCurrentCount(traces.length);
      updateFilterTotalCount(traces.length);
      updateFilterDuration(parsed.minStartTime, parsed.maxStartTime);

      $('#help-msg').hide();
      $('#error-box').hide();
      $(".infobar").show();
      $(".service-tag-list").show();
    }

    $(".filter-submit button").removeAttr('disabled');
  };

  return {
    initialize: initialize
  };
})();
