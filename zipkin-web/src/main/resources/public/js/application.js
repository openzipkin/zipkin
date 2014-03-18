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

// Place your application-specific JavaScript functions and classes here
// This file is automatically included by javascript_include_tag :defaults

/*global root_url:false Trace:false */
var Zipkin = Zipkin || {};

Zipkin.Base = (function() {

  var self = this
    , top_bar
    , trace_header
    , trace_vis
    , clockSkewListeners = []
    ;

  var addClockSkewListener = function(listener) {
    clockSkewListeners.push(listener);
  };

  /**
   * Vars for when to re-render the chart when window is resized
   */
  var prevWidth = $(window).width();

  /* Return true of window was resized, false otherwise */
  var windowResized = function (prevWidth, newWidth) {
    var a = Math.floor(newWidth / Zipkin.Config.MAX_WINDOW_SIZE);
    var b = Math.floor(prevWidth / Zipkin.Config.MAX_WINDOW_SIZE);
    /**
     * XOR the two ratios to determine whether we should re-render the trace graphic
     */
    return a ? !b : b;
  };

  var clockSkewState = function () {
    if (getCookie("clockSkewState") == null) {
      setCookie("clockSkewState", true);
    }
    return getCookie("clockSkewState") == "true";
  };

  var enableClockSkewBtn = function () {
    $('.adjust-clock-skew-btn').blur();
    $('.adjust-clock-skew-btn').removeAttr('disabled');
  };

  var disableClockSkewBtn = function () {
    $('.adjust-clock-skew-btn').attr('disabled', 'true');
  };

  /* Click callback for brand button */
  var brandClick = function (e) {
    var url = '/' + (clockSkewState() ? "" : "?adjust_clock_skew=false");
    if (e.which == 1) {
      // Left click
      window.location.assign(url);
    } else if (e.which == 2) {
      // Middle click
      window.open(url);
    }
    return false;
  };

  var setClockSkewBtnState = function(state) {
    setCookie("clockSkewState", state);
    if (state) {
      $('.adjust-clock-skew-btn').attr('value', 'true');
      $('.adjust-clock-skew-btn').removeClass('btn-danger').addClass('btn-info');
      return "Enabled";
    } else {
      $('.adjust-clock-skew-btn').attr('value', 'false');
      $('.adjust-clock-skew-btn').removeClass('btn-info').addClass('btn-danger');
      return "Disabled";
    }
  };

  /* Click callback for clock skew button */
  var clockSkewClick = function (e) {
    // Disable the button to prevent multiple requests
    // Re-enable the button after ajax requests have completed
    disableClockSkewBtn();

    // Hide the tooltip
    $('.adjust-clock-skew-tooltip').tooltip('hide');

    // Toggle the button state
    var tooltip_text = "Clock skew adjustment: " + setClockSkewBtnState(!clockSkewState());

    // Fire the listeners
    $.each(clockSkewListeners, function (i, e) {
      e();
    });

    $('.adjust-clock-skew-tooltip').attr('data-original-title', tooltip_text);
    return false;
  };

  var initialize = function () {

    $.each(Zipkin.Util.TEMPLATES, function(key, name) {
      Zipkin.Util.templatize(name, $.noop);
    });

    // Bind click handler for brand button
    $(".brand").click(brandClick);

    // Hook up trace nav buttons
    $(".js-zipkin-navbar > li").click(function () {
        if (!$(this).hasClass("active")) {
          $(".js-zipkin-navbar > li.active").removeClass("active");
          $(this).addClass("active");
        }
    });

    // Set clock skew button to whatever the cookie says
    var tooltip_text = "Clock skew adjustment: " + setClockSkewBtnState(clockSkewState());
    $('.adjust-clock-skew-tooltip').attr('data-original-title', tooltip_text);

    // Bind click handler for clock skew button
    $('.adjust-clock-skew-btn').click(clockSkewClick);

    // Bind tooltip for clock skew button
    $('.adjust-clock-skew-btn').on('hover', function(e) {
      $('.adjust-clock-skew-tooltip').tooltip({placement: "left"});
    });

    /* Bind table-sortable handlers */
    $(document).on("click", ".table-sortable th", function(e) {
      var th = e.target;

      if (th.children.length === 0) {
        // This is not the current sort column
        // Iterate through the siblings and remove the sort column
        var icon = null;
        $(th).siblings().each(function(i, e) {
          if (e.children.length > 0) {
            icon = $(e.children[0]);
            icon.detach();
          }
        });

        // Make this the sort column
        icon.removeClass('icon-arrow-up').addClass('icon-arrow-down');
        $(th).append(icon);
      } else {

        $(th).find('i').toggleClass("icon-arrow-up").toggleClass("icon-arrow-down");
      }

      var sortDesc = true;
      if ($(th).find('i').hasClass("icon-arrow-up")) {
        sortDesc = false;
      }

      var ths = $(th).parents(".table-sortable").find('th');
      var index = 0;
      ths.each(function(i, e) {
        if (e == th) {
          index = i + 1; // Fix since :nth-child selector is 1-indexed
        }
      });

      var body = $(th).parents(".table-sortable").find('tbody');
      var rows = body.children();
      rows.sort(function(a, b) {
        var aObj = $(a).find(":nth-child(" + index + ")").html();
        var bObj = $(b).find(":nth-child(" + index + ")").html();
        var aNum, bNum;
        if ((aNum = parseFloat(aObj)) && (bNum = parseFloat(bObj))) {
          aObj = aNum;
          bObj = bNum;
        }
        if (aObj == bObj) {
          return 0;
        } else if (sortDesc) {
          return aObj > bObj ? -1 : 1;
        } else {
          return aObj > bObj ? 1 : -1;
        }
      });

      rows.each(function(i, e) {
        $(e).detach();
      });
      body.append(rows);
    });
  };

  /* Store a session cookie */
  var setCookie = function(key, value) {
    document.cookie = key + "=" + value;
  };

  /* Gets the value for a cookie key, null if not exists */
  var getCookie = function(key) {
    var found = null;
    $.each(document.cookie.split(";"), function(i, kv) {
      var line = kv.trim();
      var split = line.indexOf("=");
      if (split >= 0) {
        if (line.slice(0, split) === key) {
          found = line.slice(split+1);
        }
      }
    });
    return found;
  };

  return {
    windowResized: windowResized,

    addClockSkewListener: addClockSkewListener,

    initialize: initialize,

    setCookie: setCookie,
    getCookie: getCookie,

    clockSkewState: clockSkewState,
    enableClockSkewBtn: enableClockSkewBtn,
    disableClockSkewBtn: disableClockSkewBtn
  };
})();
