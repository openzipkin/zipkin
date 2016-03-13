import {component} from 'flightjs';
import queryString from 'query-string';

export default component(function trace() {
  this.spans = {};
  this.parents = {};
  this.children = {};
  this.spansByService = {};
  this.spansBackup = {};
  /*this is for a temporary rectangle which is shown on
   * user's mouse move over span view.*/
  this.rectElement = $('<div>').addClass('rect-element');

  this.setupSpan = function($span) {
    var self = this;
    var id = $span.data('id');

    $span.id = id;
    $span.expanded = false;
    $span.$expander = $span.find('.expander');
    $span.inFilters = 0;
    $span.openChildren = 0;
    $span.openParents = 0;
    this.spans[id] = $span;

    var children = ($span.data('children') || "").toString().split(',');
    if (children.length == 1 && children[0] === "") {
      $span.find('.expander').hide();
      children = [];
    }
    this.children[id] = children;

    var parentId = $span.data('parentId');
    $span.isRoot = !(parentId !== undefined && parentId !== "");
    this.parents[id] = !$span.isRoot ? [parentId] : [];
    $.merge(this.parents[id], this.parents[parentId] || []);

    $.each(($span.data('serviceNames') || "").split(','), function(i, sn) {
      var spans = self.spansByService[sn] || [];
      spans.push(id);
      self.spansByService[sn] = spans;
    });
  };

  /*This method stores original span details for later use.
   * When span view is zoomed in and zoomed out these details help to
   * get back to original span view*/
  this.setupSpansBackup = function($span) {
      var id = $span.data('id');
      $span.id = id;
      this.spansBackup[id] = $span;
    };

  /*Returns a jquery object representing the spans in svc*/
  this.getSpansByService = function(svc) {
    var spans = this.spansByService[svc];
    if (spans === undefined)
      this.spansByService[svc] = spans = $();
    else if (spans.jquery === undefined)
      this.spansByService[svc] = spans = $('#' + spans.join(',#'));
    return spans;
  };

  this.filterAdded = function(e, data) {
    if (this.actingOnAll) return;
    var self = this;
    var spans = this.getSpansByService(data.value).map(function() {
      return self.spans[$(this).data('id')];
    });
    this.expandSpans(spans);
  };

  this.expandSpans = function(spans) {
    var self = this,
        toShow = {};
    $.each(spans, function(i, $span) {
      if ($span.inFilters == 0)
        $span.show().addClass('highlight');
      $span.expanded = true;
      $span.$expander.text('-');
      $span.inFilters += 1;

      $.each(self.children[$span.id], function(i, cId) { toShow[cId] = true; self.spans[cId].openParents += 1; });
      $.each(self.parents[$span.id], function(i, pId) { toShow[pId] = true; self.spans[pId].openChildren += 1; });
    });

    $.each(toShow, function(id, n) { self.spans[id].show(); });
  };

  this.filterRemoved = function(e, data) {
    if (this.actingOnAll) return;
    var self = this;
    var spans = this.getSpansByService(data.value).map(function() {
      return self.spans[$(this).data('id')];
    });
    this.collapseSpans(spans);
  };

  this.collapseSpans = function(spans, childrenOnly) {
    var self = this,
        toHide = {};

    $.each(spans, function(i, $span) {
      $span.inFilters -= 1;
      if (!childrenOnly && $span.inFilters == 0) {
        $span.removeClass('highlight');
        self.hideSpan($span);
      }

      $span.expanded = false;
      $span.$expander.text('+');

      $.each(self.children[$span.id], function(i, cId) { toHide[cId] = true; self.spans[cId].openParents -= 1; });
      if (!childrenOnly)
        $.each(self.parents[$span.id], function(i, pId) { toHide[pId] = true; self.spans[pId].openChildren -= 1; });
    });

    $.each(toHide, function(id, n) { self.hideSpan(self.spans[id]); });
  };

  this.hideSpan = function($span) {
    if ($span.inFilters > 0 || $span.openChildren > 0 || $span.openParents > 0) return;
    $span.hide();
  };

  this.handleClick = function(e) {
    var $target = $(e.target);
    var $span = this.spans[($target.is('.span') ? $target : $target.parents('.span')).data('id')];

    var $expander = $target.is('.expander') ? $target : $target.parents('.expander');
    if ($expander.length > 0) {
      this.toggleExpander($span);
      return;
    }

    /*incase mouse moved to another span after mousedown, $span is undefined*/
    if ($span && $span.length > 0) {
      this.showSpanDetails($span);
      return;
    }
  };

  /*On mousedown and mousemove we need to show a selection area and zoomin
   * spans according to width of selected area. During zoomin only the
   * width i.e. x coordinates are considered.*/
  this.handleMouseDown = function(e) {
    var self = this;

    var rectTop = e.pageY,
        rectLeft = e.pageX;
    self.rectElement.appendTo(self.$node);

    /*dont draw the rectangle until mouse is moved.
     * this helps in getting the correct parent in case of click
     * event.*/
    self.rectElement.css({top: '0px',left: '0px',width: '0px',height: '0px'});

    self.$node.bind('mousemove', function(e){
      /*prevent selection and thus highlighting of spans after mousedown and mousemove*/
      e.preventDefault();

      /*draw a rectangle out of mousedown and mousemove coordinates*/
      var rectWidth = Math.abs(e.pageX - rectLeft),
          rectHeight = Math.abs(e.pageY - rectTop);

      var new_x = (e.pageX < rectLeft) ? (rectLeft - rectWidth) : rectLeft,
          new_y = (e.pageY < rectTop) ? (rectTop - rectHeight) : rectTop;

      self.rectElement.css({top: new_y + 'px', left: new_x + 'px',
          width: rectWidth + 'px', height: rectHeight + 'px'});
    });

    self.$node.bind('mouseup', function(e){
      self.$node.unbind('mousemove');
      self.$node.unbind('mouseup');
      /*Add code to calculate mintime and max time from pixel value of
       * mouse down and mouse move*/
      var originalDuration = parseFloat($('#timeLabel-backup .time-marker-5').text()),
          spanClickViewLeftOffsetPx = $($('#trace-container .time-marker-0')[1]).offset().left,
          spanClickViewWidthPx = $('#trace-container .time-marker-5').position().left;

      var rectTopLeft = self.rectElement.position().left;
      /*make sure that redraw mintime starts from 0.0 not less than 0.0.
       * if user starts selecting from servicename adjust the left, width accordingly*/
      var rectElementActualLeft =
        (rectTopLeft < spanClickViewLeftOffsetPx) ? spanClickViewLeftOffsetPx : rectTopLeft;
      var rectElementActualWidth =
        (rectTopLeft < spanClickViewLeftOffsetPx) ?
        (self.rectElement.width() - (spanClickViewLeftOffsetPx - rectTopLeft)) : self.rectElement.width();

      var minTimeOffsetPx = rectElementActualLeft - spanClickViewLeftOffsetPx;
      var maxTimeOffsetPx = (rectElementActualLeft  + rectElementActualWidth) - spanClickViewLeftOffsetPx;

      var minTime = minTimeOffsetPx * (originalDuration/spanClickViewWidthPx);
      var maxTime = maxTimeOffsetPx * (originalDuration/spanClickViewWidthPx);

      /*when mousemove doesnt happen mintime is greater than maxtime. since rect-element
       * is created at top left corner of screen, rectElementActualWidth will be negative.
       *We need to invoke mouseclick functionality*/
      if(minTime >= maxTime){
        /*pass on the target which got clicked. Since we do not draw
         * rectangle on mousedown, we would never endup having
         * rect-element as our target. Target would always be either
         * handle, time-marker, duration which are all children of span class*/
        self.handleClick(e.target);
      }else {
        /*now that we have min and max time, trigger zoominspans*/
        self.trigger(document, 'uiZoomInSpans', {mintime: minTime, maxtime:maxTime});
      }
      self.rectElement.remove();
    });
  };


  this.toggleExpander = function($span) {
    if ($span.expanded)
      this.collapseSpans([$span], true);
    else
      this.expandSpans([$span], true);
  };

  this.showSpanDetails = function($span) {
    var spanData = {
      annotations: [],
      binaryAnnotations: []
    };

    $.each($span.data('keys').split(','), function(i, n) {
      spanData[n] = $span.data(n);
    });

    $span.find('.annotation').each(function() {
      var $this = $(this);
      var anno = {};
      $.each($this.data('keys').split(','), function(e, n) {
        anno[n] = $this.data(n);
      });
      spanData.annotations.push(anno);
    });

    $span.find('.binary-annotation').each(function() {
      var $this = $(this);
      var anno = {};
      $.each($this.data('keys').split(','), function(e, n) {
        anno[n] = $this.data(n);
      });
      spanData.binaryAnnotations.push(anno);
    });

    this.trigger('uiRequestSpanPanel', spanData);
  };

  this.showSpinnerAround = function(cb, e, data) {
    if (this.actingOnAll) return cb(e, data);

    this.trigger(document, 'uiShowFullPageSpinner');
    var self = this;
    setTimeout(function() {
      cb(e, data);
      self.trigger(document, 'uiHideFullPageSpinner');
    }, 100);
  };

  this.triggerForAllServices = function(evt) {
    var self = this;
    $.each(self.spansByService, function(sn, s) { self.trigger(document, evt, {value: sn}); });
  };

  this.expandAllSpans = function() {
    var self = this;
    self.actingOnAll = true;
    this.showSpinnerAround(function() {
      $.each(self.spans, function(id, $span) {
        $span.inFilters = 0;
        $span.show().addClass('highlight');
        $span.expanded = true;
        $span.$expander.text('-');
        $.each(self.children[id], function(i, cId) { self.spans[cId].openParents += 1; });
        $.each(self.parents[id], function(i, pId) { self.spans[pId].openChildren += 1; });
      });
      /*Emulates getAllSpans(serviceNames) by looping on getSpansByService(svc)*/
      $.each(self.spansByService, function(svc, spans) {
        $.each(self.getSpansByService(svc), function(i, $span) {
          $span.inFilters += 1;
        });
      });
      self.triggerForAllServices('uiAddServiceNameFilter');
    });
    self.actingOnAll = false;
  };

  this.collapseAllSpans = function() {
    var self = this;
    self.actingOnAll = true;
    this.showSpinnerAround(function() {
      $.each(self.spans, function(id, $span) {
        $span.inFilters = 0;
        $span.openParents = 0;
        $span.openChildren = 0;
        $span.removeClass('highlight');
        $span.expanded = false;
        $span.$expander.text('+');
        if (!$span.isRoot) $span.hide();
      });
      self.triggerForAllServices('uiRemoveServiceNameFilter');
    });
    self.actingOnAll = false;
  };

  /*This method modifies the span container view. It zooms in the span view for selected time duration.
   * Spans starting with in the selected time duration are highlighted with span name in red color.
   * Also unhides zoomout button so that user can click to go back to original span view*/
  this.zoomInSpans = function(node, data) {
    var self = this;

    var originalDuration = parseFloat($('#timeLabel-backup .time-marker-5').text());

    var mintime = data.mintime,
        maxtime = data.maxtime,
        newDuration = maxtime - mintime;

    self.$node.find('#timeLabel .time-marker').each(function(i) {
      var v = (mintime + newDuration * (i/5)).toFixed(2);
      //TODO:show time units according to new duration
      $(this).text(v);
      $(this).css('color', "#d9534f");
    });

    var styles = {
      left : "0.0%",
      width: "100.0%",
      color: "#000"
    };
    self.showSpinnerAround(function() {
      $.each(self.spans, function(id, $span) {
        /*corresponding to this id extract span from backupspans list*/
        var origLeftVal = parseFloat((self.spansBackup[id]).find('.duration')[0].style.left),
            origWidthVal = parseFloat((self.spansBackup[id]).find('.duration')[0].style.width);
        /*left and width are in %, so convert them as time values*/
        var spanStart = (origLeftVal*originalDuration)/100,
            spanEnd = spanStart + (origWidthVal*originalDuration)/100;
        /*reset the color to black. It gets set for inrange spans to red*/
        styles.color = "#000";

        /*change style left, width and color of new spans based on mintime and maxtime*/
        if(spanStart < mintime && spanEnd < mintime) {
          styles.left = "0.0%"; styles.width = "0.0%";
        } else if (spanStart < mintime && spanEnd > mintime && spanEnd < maxtime) {
          var w = (((spanEnd - mintime))/newDuration) * 100 + "%";
          styles.left = "0.0%"; styles.width = w;
        } else if (spanStart < mintime && spanEnd > mintime && spanEnd > maxtime) {
          styles.left = "0.0%"; styles.width = "100.0%";
        } else if (spanStart >= mintime && spanStart < maxtime && spanEnd <= maxtime) {
          var l = (((spanStart - mintime))/newDuration) * 100 + "%";
          var w = (((spanEnd - spanStart))/newDuration) * 100 + "%";
          styles.left = l; styles.width = w; styles.color = "#d9534f";
        } else if (spanStart >= mintime && spanStart < maxtime && spanEnd > maxtime) {
          var l = (((spanStart - mintime))/newDuration) * 100 + "%";
          var w = (((maxtime - spanStart))/newDuration) * 100 + "%";
          styles.left = l; styles.width = w; styles.color = "#d9534f";
        } else if (spanStart > maxtime) {
          styles.left = "100.0%"; styles.width = "0.0%";
        } else if (spanStart == maxtime) {
          styles.left = "100.0%"; styles.width = "0.0%"; styles.color = "#d9534f";
        } else {
          styles.left = "0.0%"; styles.width = "0.0%";
        }

        $span.find('.duration').css('color', styles.color);
        $span.find('.duration').animate({left: styles.left, width: styles.width}, "slow");
      });

    });

    /*make sure that zoom-in on already zoomed spanview is not allowed*/
    self.$node.unbind('mousedown');

    /*show zoomOut button now*/
    $('button[value=uiZoomOutSpans]').show("slow");
  };


  /*This method brings back the original span container in view*/
  this.zoomOutSpans = function() {
    /*re bind mousedown event to enable zoom-in after zoom-out*/
    this.on('mousedown', this.handleMouseDown);
    var originalDuration = parseInt($('#timeLabel-backup .time-marker-5').text(), 10);

    /*get values from the backup trace container*/
    this.$node.find('#timeLabel .time-marker').each(function(i) {
      $(this).css('color', $('#timeLabel-backup .time-marker-'+i).css('color'));
      $(this).text($('#timeLabel-backup .time-marker-'+i).text());
    });

    var self = this;
    this.showSpinnerAround(function() {
      $.each(self.spans, function(id, $span) {
        var originalStyleLeft = (self.spansBackup[id]).find('.duration')[0].style.left,
            originalStyleWidth = (self.spansBackup[id]).find('.duration')[0].style.width;

        $span.find('.duration').animate({left: originalStyleLeft, width: originalStyleWidth}, "slow");
        $span.find('.duration').css('color', '#000');
      });
    });

    /*hide zoomOut button now*/
    $('button[value=uiZoomOutSpans]').hide("slow");
  };

  this.after('initialize', function() {
    this.around('filterAdded', this.showSpinnerAround);
    this.around('filterRemoved', this.showSpinnerAround);

    this.on('click', this.handleClick);
    this.on('mousedown', this.handleMouseDown);

    this.on(document, 'uiAddServiceNameFilter', this.filterAdded);
    this.on(document, 'uiRemoveServiceNameFilter', this.filterRemoved);

    this.on(document, 'uiExpandAllSpans', this.expandAllSpans);
    this.on(document, 'uiCollapseAllSpans', this.collapseAllSpans);
    this.on(document, 'uiZoomInSpans', this.zoomInSpans);
    this.on(document, 'uiZoomOutSpans', this.zoomOutSpans);

    var self = this;
    self.$node.find('.span:not(#timeLabel)').each(function() { self.setupSpan($(this)); });
    /*get spans from trace-container-backup*/
    $('#trace-container-backup .span:not(#timeLabel-backup)').each(function() { self.setupSpansBackup($(this)); });

    var serviceName = queryString.parse(location.search).serviceName;
    if (serviceName !== undefined)
      this.trigger(document, 'uiAddServiceNameFilter', {value: serviceName});
    else
      this.expandSpans([this.spans[this.$node.find('.span:nth(1)').data('id')]]);
  });
});
