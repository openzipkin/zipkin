'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(trace);

    function trace() {
      this.spans = {};
      this.parents = {};
      this.children = {};
      this.spansByService = {};

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

        if ($span.length > 0) {
          this.showSpanDetails($span);
          return;
        }
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
          $.each(self.spansByService, function(svc, spans) {
            $.each(spans, function(i, $span) { $span.inFilters += 1; });
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

      this.after('initialize', function() {
        this.around('filterAdded', this.showSpinnerAround);
        this.around('filterRemoved', this.showSpinnerAround);

        this.on('click', this.handleClick);
        this.on(document, 'uiAddServiceNameFilter', this.filterAdded);
        this.on(document, 'uiRemoveServiceNameFilter', this.filterRemoved);

        this.on(document, 'uiExpandAllSpans', this.expandAllSpans);
        this.on(document, 'uiCollapseAllSpans', this.collapseAllSpans);

        var self = this;
        self.$node.find('.span:not(#timeLabel)').each(function() { self.setupSpan($(this)); });

        var serviceName = $.getUrlVar('serviceName');
        if (serviceName !== undefined)
          this.trigger(document, 'uiAddServiceNameFilter', {value: serviceName});
        else
          this.expandSpans([this.spans[this.$node.find('.span:nth(1)').data('id')]]);
      });
    };
  }
)
