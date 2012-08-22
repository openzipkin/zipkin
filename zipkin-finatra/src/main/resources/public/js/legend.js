///*jslint indent: 2, browser: true*/
///*global $, viz*/
//
//(function (Zipkin) {
//  'use strict';
//
//  var
//    // uninitialized variables
//    visible, headerHeight,
//    $element, $rows, $title,
//
//    // functions handlers
//    update, hide, show, buildRows, setPosition, setRows, isVisible, scroll,
//
//    // initialized variables
//    rowsAvailable = 0,
//    $win = $(window),
//
//    // regular expressions
//    reDelimiters = /([_\.:\s\/])/g, // _, ., :, space or /
//
//    // functions shorthands
////    utilData = viz.util.data,
////    formatXAxis = utilData.formatXAxis,
////    formatYAxis = utilData.formatYAxis,
//
//    // classes and properties shorthands
//    CLASS_INVISIBLE = 'invisible',
//    CLASS_HIDDEN = 'hidden',
//    CLASS_WRAP = 'wrap',
//    MAX_HEIGHT = 'max-height',
//    FIRST = ':first',
//    AUTO = 'auto',
//
//    // markup templates
//    TEMPLATE_LEGEND =
//    '<div id="legend" class="legend invisible">' +
//    '  <div class="header">' +
//    '    <span class="legend-title time"></span>' +
//    '  </div>' +
//    '  <ul class="legend-data"></ul>' +
//    '</div>',
//
//    TEMPLATE_ROW =
//    '<li class="timeseries-row active">' +
//    '  <div class="color-patch"></div>' +
//    '  <div class="metric-value"></div>' +
//    '  <div class="metric-label"></div>' +
//    '</li>';
//
//  /**
//   * Build empty rows markup
//   * @param {Number} n The number of rows to build
//   * @return {String} The rows markup
//   */
//  buildRows = function (n) {
//    var i,
//      markup = '';
//
//    for (i = 0; i < n; i += 1) {
//      markup += TEMPLATE_ROW;
//    }
//
//    return markup;
//  };
//
//  /**
//   * Create or hide rows to accomodate the current chart timeseries
//   * @param {Number} numRows The number of rows to be displayed
//   */
//  setRows = function (numRows) {
//    var i, $row;
//
//    // Append new rows
//    if (numRows > rowsAvailable) {
//      $rows.append(buildRows(numRows - rowsAvailable));
//      rowsAvailable = numRows;
//    } else {
//      // Unhide rows and hide unused rows
//      $row = $rows.find(FIRST);
//      for (i = 0; i < rowsAvailable; i += 1) {
//        if (i < numRows) {
//          $row.removeClass(CLASS_HIDDEN);
//        } else {
//          $row.addClass(CLASS_HIDDEN);
//        }
//        $row = $row.next();
//      }
//    }
//  };
//
//  /**
//   * Update legend rows values from current chart hover position
//   * @param {Date} hoverTime The current time from chart mouse hover
//   * @param {Object} points The chart data points / timeseries
//   */
//  update = function (hoverTime, points) {
//    var i, len, item, ts, active, value, $row;
//
//    // Set title (time)
//    $title.html(formatXAxis(hoverTime));
//
//    // Update rows
//    $row = $rows.find(FIRST);
//    for (i = 0, len = points.length; i < len; i += 1) {
//      item = points[i];
//      ts = item.timeseries;
//
//      // Color uses colorID that maps to css .color class
//      $row.find('.color-patch:first').removeClass()
//        .addClass('color-patch color' + ts.colorID);
//
//      // Metric label
//      $row.find('.metric-label:first').html(
//        ts.label.replace(reDelimiters, '$1&shy;')
//      );
//
//      // Value
//      value = item.dataPoint;
//      if (value) {
//        // Use the same formatting code that y-axis
//        // uses when displaying the data
//        // TODO: The ticks obj requirement is kinda ghetto. Figure out how to
//        // abstract that part of the formatter out
//        value = formatYAxis(value[1], {ticks: [1, 2, 3]});
//      } else {
//        value = 'null';
//      }
//      $row.find('.metric-value:first').html(value);
//
//      // Next row
//      $row = $row.next();
//    }
//  };
//
//  /**
//   * Set the best position to display legend in  this order:
//   * - on the left side of chart if it fits
//   * - on the right side of the chart if it fits
//   * - centralized below or above the chart whichever has the greatest height
//   * If legend is greatest then viewport bottom:
//   * - right align bottom if still fits in the viewport height
//   * - otherwise set legend height equals to viewport height and add scrolling
//   * @param {Object} chartPosition the current chart position and dimensions
//   */
//  setPosition = function (chartPosition) {
//    var x, height, width, topHeight, bottomHeight, elementHeight, elementWidth,
//      y = chartPosition.y,
//      windowWidth = $win.width(),
//      windowHeight = $win.height(),
//      scrollTop = $win.scrollTop();
//
//    // Get header height once
//    if (!headerHeight) {
//      headerHeight = $element.find('.header').outerHeight();
//    }
//    // Set maximum height to avoid viewport overflow
//    $rows.css(MAX_HEIGHT, windowHeight - headerHeight - 10);
//    elementHeight = $element.outerHeight();
//
//    // Check to see if the legend will overflow screen vertically
//    height = y + elementHeight - scrollTop;
//    if (height > windowHeight) {
//      // Larger than viewport: align bottoms
//      y -= (height - windowHeight);
//    }
//    if (y < scrollTop) {
//      // Above the viewport top fold: align tops
//      y += (scrollTop - y);
//    }
//
//    // Set the best fixed width to avoid annoying dynamic resizing
//    $element
//      .width(AUTO)
//      .width($element.width());
//    elementWidth = $element.outerWidth();
//    // For very narrow viewport wrap metric labels
//    // extra 30px is added as margin of error due to scrollbar presence
//    if (elementWidth + 30 >= windowWidth) {
//      $element
//        .addClass(CLASS_WRAP)
//        .width(AUTO)
//        .width($element.width());
//      elementWidth = $element.outerWidth();
//    }
//
//    // Check to see if the legend will overflow screen horizontally
//    width = chartPosition.x + chartPosition.width;
//    if ((width + elementWidth) >= windowWidth) {
//      // overflowing to the right, try to the left first
//      width = chartPosition.x - elementWidth;
//      if (width >= 0) {
//        // positioning to the left
//        x = width;
//      } else {
//        // centralize
//        x = chartPosition.x +
//          parseInt((chartPosition.width - elementWidth) / 2, 10);
//        // but make sure it won't be positioned offscreen
//        if ((x + elementWidth) > windowWidth) {
//          x = windowWidth - elementWidth;
//        }
//        if (x < 0) {
//          x = 0;
//        }
//        y = chartPosition.y;
//        topHeight = y - scrollTop;
//        bottomHeight = windowHeight - (topHeight + chartPosition.height);
//        if (bottomHeight < topHeight) {
//          // above chart
//          if (elementHeight > topHeight) {
//            $rows.css(MAX_HEIGHT, topHeight - headerHeight - 10);
//            elementHeight = $element.outerHeight();
//          }
//          y -= elementHeight;
//        } else {
//          // below chart
//          if (elementHeight > bottomHeight) {
//            $rows.css(MAX_HEIGHT, bottomHeight - headerHeight - 10);
//            elementHeight = $element.outerHeight();
//          }
//          y += chartPosition.height;
//        }
//      }
//    } else {
//      // positioning to the right
//      x = width;
//    }
//
//    // Set legend fixed position
//    $element.offset({left: x, top: y});
//  };
//
//  /**
//   * Hide legend by simply making the whole container invisible
//   */
//  hide = function () {
//    $element
//      .addClass(CLASS_INVISIBLE)
//      .removeClass(CLASS_WRAP);
//    visible = 0;
//  };
//
//  /**
//   * Show legend by building and updating rows then making it visible
//   * @param {Number} numRows The number of rows on in the legend
//   * @param {Object} chartPosition The current chart position and dimensions
//   * @param {Date} hoverTime The current time from chart mouse hover
//   * @param {Object} points The chart data points / timeseries
//   */
//  show = function (numRows, chartPosition, hoverTime, points) {
//    setRows(numRows);
//    update(hoverTime, points);
//    setPosition(chartPosition);
//    $element.removeClass(CLASS_INVISIBLE);
//    visible = 1;
//  };
//
//  /**
//   * Scroll timeseries rows by a % factor
//   * @param {Number} scrollBy The % factor to scroll rows.
//   */
//  scroll = function (scrollBy) {
//    $rows.scrollTop(scrollBy *
//      ($rows[0].scrollHeight - $rows.height()));
//  };
//
//  /**
//   * Check the current state of legend, either visible or not.
//   * Checking the presence of legend class name "invisible" could be used too,
//   * however, chart plot hover triggers too often, so it's better off not
//   * touching the DOM, for better performance
//   * @return {Boolean} Legend is visible or not
//   */
//  isVisible = function () {
//    return visible === 1;
//  };
//
//  // Initialize
//  $('body').append(TEMPLATE_LEGEND);
//  $element = $('#legend');
//  $rows = $element.find('.legend-data');
//  $title = $element.find('.legend-title');
//
//  // Expose methods/properties
//  Zipkin.Legend = {
//    show: show,
//    hide: hide,
//    scroll: scroll,
//    update: update,
//    isVisible: isVisible
//  };
//}(Zipkin));
