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

  /* Makes ajax call to get and populate the service names field */
  var fetchServiceNames = function() {
    $.ajax({
      type: 'GET',
      url: root_url + 'traces/services_json/',
      success: function(data){
        var select = $("#service_name");

        /* Use service name from cookie if exists */
        var lastServiceName = Zipkin.Base.getCookie("lastServiceName");

        for (var i = 0; i < data.length; i++) {
          var d = data[i];
          var option = $('<option>').val(d).text(d);
          if (lastServiceName === d) {
            option.attr('selected', 'selected');
          }
          select.append(option);
        }

        $("select#service_name").change(); // To trigger the span loading below
        $(".filter-submit button").attr('disabled', true);
      },
      error: function(xhr, status, error) {
        $('#help-msg').hide();
        $('#error-msg').text('Could not fetch service names. Query daemon might be down.');
        $('#error-box').show();
      }
    });
  };

  var serviceNameChange = function(){
    // We don't want to display the previous service's spans
    // So we wipe it and just put "all" in there while we wait for the response
    $('#span_name').text('');

    var service_name = $(this).val()
      , selected_service = { 'service_name': service_name }
      ;

    $.ajax({
      type: 'GET',
      url: root_url + 'traces/spans_json/',
      data: selected_service,
      success: function(data){
        var spanSelector = $('#span_name');

        /* Use span name from cookie if exists */
        var lastSpanName = Zipkin.Base.getCookie("lastSpanName");

        var allOption = $('<option>').val("all").text("all");
        if (lastSpanName === "all") {
          allOption.attr('selected', 'selected');
        }
        spanSelector.append(allOption);

        for(var i = 0; i < data.length; i++) {
          var d = data[i];
          var option = $('<option>').val(d).text(d);
          if (lastSpanName === d) {
            option.attr('selected', 'selected');
          }
          spanSelector.append(option);
        }

        spanSelector.change();
        $(".filter-submit button").removeAttr('disabled');
      },
      error: function(xhr, status, error) {
        $('#help-msg').hide();
        $('#error-msg').text('Could not fetch span names. Query daemon might be down.');
        $('#error-box').show();
      }
    });

    /* Fetch top annotations for this service */
    $.ajax({
      type: 'GET',
      url: root_url + 'traces/top_annotations',
      data: selected_service,
      success: function(data) {
        if (data.length > 0) {
          $("#time_annotation").autocomplete({source: data});
        }
      }
    });

    /* Fetch top key value annotations for this service */
    $.ajax({
      type: 'GET',
      url: root_url + 'traces/top_kv_annotations',
      data: selected_service,
      success: function(data) {
        if (data.length > 0) {
          $("#annotation_key").autocomplete({source: data});
        }
      }
    });
  };

  var initialize = function() {
    Zipkin.Base.initialize();

    fetchServiceNames();

    /* Populate span selector after a service has been selected */
    $('#service_name').change(serviceNameChange);

    /**
     * Helper functions for trace query results
     */

    /* Adds a service tag to the service tag list */
    var addServiceTag = function(service_name, closeable) {
      if ($("span[id*='service-tag-" + service_name + "']").length === 0) {
        templatize(TEMPLATES.SERVICE_TAG, function(template) {
          var context = { name : service_name, closeable: closeable };
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

    /* Filter the query results based on service tag list */
    var filterQueryResults = function (services) {
      if (!services) {
        return traceData;
      }
      return $.grep(Zipkin.Util.shallowCopy(traceData), function (e, i) { // why the hell are these inverted
        var satisfied = true;
        $.each(services, function (key, value) {
          var service_satisfied = false;
          $.each(e.service_counts, function (i, e) {
            if (key == e[0]) {
              service_satisfied = true;
            }
          });
          satisfied = satisfied && service_satisfied;
        });
        return satisfied;
      });
    };

    var sortQueryResults = function(data) {
      /* Option index directly maps to the correct sort order */
      var sortOrder = getSortOrder();

      data.sort(function(a, b) {
        if (sortOrder == ORDER_TIMESTAMP_ASC) {
          return new Date(a.start_time) - new Date(b.start_time);
        } else if (sortOrder == ORDER_TIMESTAMP_DESC) {
          return new Date(b.start_time) - new Date(a.start_time);
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
        $.each(t.service_counts, function (i, s) {
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

      $(".filter-duration").text(delta + " " + suffix);
    };

    /* Click handler for adding a service filter */
    var labelClick = function (event) {
      event.stopPropagation();
      var target = $(event.target);
      var service_name = target.attr("value");
      addServiceTag(service_name, true);

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
      var service_name = target.attr('id').slice("service-tag-close-".length);

      $("li[id*='service-tag-li-" + service_name + "']").remove();

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
    var filter_submit = function(adjust_clock_skew) {

      // Show some loading stuff while we wait for the query
      $('#help-msg').hide();
      $('#error-box').hide();
      $(".infobar").hide();
      $('#query-results').hide();
      $('#loading-data').show();

      $('.tab-content > div').each(function(){
        // Clear fields in inactive tabs before submission so server processes the field of the user's intent
        if(!$(this).hasClass('active')) {
          if($(this).find('input').length !== 0) {
            $(this).find('input').val('');
          } else {
            $(this).find('select').val('all');
          }
        }
      });

      var service_name = $('select[name=service_name]').val();
      var spanName = $('select[name=span_name]').val()

      /* Cookie this service */
      Zipkin.Base.setCookie("lastServiceName", service_name);
      Zipkin.Base.setCookie("lastSpanName", spanName);

      // Let's fetch the prerendered results
      var query = {
        "service_name"      : service_name,
        "end_date"          : $('input[name=end_date]').val(),
        "end_time"          : $('input[name=end_time]').val(),
        "limit"             : $('input[name=limit]').val(),
        "span_name"         : spanName,
        "time_annotation"   : $('input[name=time_annotation]').val(),
        "annotation_key"    : $('input[name=annotation_key]').val(),
        "annotation_value"  : $('input[name=annotation_value]').val(),
        "adjust_clock_skew" : Zipkin.Base.clockSkewState() ? 'true' : 'false'
      };

      var url_method = 'traces/query';

      $.ajax({
        type: 'GET',
        url: root_url + url_method,
        data: query,
        success: function(data){
          if (data.length === 0) {
            $('#loading-data').hide();
            $('#error-msg').text("Didn't find any traces for this search :(");
            $('#error-box').show();
            return;
          } else {
            $("#error-box").hide();
          }

          traceData = data;

          $(".service-tags").empty();
          addServiceTag(service_name);


          var minStartTime = Number.MAX_VALUE
            , maxStartTime = Number.MIN_VALUE
            ;
          var maxTime = data[0].duration;
          var traces = $.map(data, function(e) {
            minStartTime = minStartTime < e.start_time ? minStartTime : e.start_time;
            maxStartTime = maxStartTime > e.start_time ? maxStartTime : e.start_time;

            e.width = (e.duration / maxTime) * 100;
            return e;
          });
          traces = updateFilteredServices(traces);

          sortQueryResults(traces);

          templatize(TEMPLATES.QUERY, function(template) {
            var context = { traces: traces };
            var content = template.render(context);
            $('#loading-data').hide();
            refreshQueryResults(content);
            Zipkin.Base.enableClockSkewBtn();
          });

          updateFilterCurrentCount(data.length);
          updateFilterTotalCount(data.length);
          updateFilterDuration(minStartTime, maxStartTime);

          $(".infobar").show();
          $(".service-tag-list").show();
        },
        error: function(xhr, status, error) {
          $('#query-results').hide();
          $('#loading-data').hide();
          $('#error-msg').text('Could not fetch trace data. Sorry buddy.');
          $('#error-box').show();
          Zipkin.Base.enableClockSkewBtn();
        }
      });

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
  };

  return {
    initialize: initialize
  };
})();
