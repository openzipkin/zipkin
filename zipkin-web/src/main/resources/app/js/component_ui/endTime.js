'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(endTime);

    function endTime() {
      this.init = function () {
        this.$endTimestamp = this.$node.find("[name=endTimestamp]");
        this.$endDate = this.$node.find(".date-input");
        this.$endTime = this.$node.find(".time-input");
        var timestamp = this.$endTimestamp.val();
        this.setDateTime((timestamp) ? moment(timestamp / 1000) : moment());
      }
      this.setDateTime = function(time) {
        this.$endDate.val(time.format("MM-DD-YYYY"));
        this.$endTime.val(time.format("HH:mm:ss"));
      }
      this.setTimestamp = function(time) {
        this.$endTimestamp.val(time.valueOf() * 1000);
      }
      this.dateChanged = function (e) {
        this.setTimestamp(moment(e.date));
      }
      this.timeChanged = function () {
        var time = moment(this.$endDate.val(), "MM-DD-YYYY");
        time.add(moment.duration(this.$endTime.val()));
        this.setTimestamp(time);
      }
      this.after('initialize', function () {
        this.init();
        this.on(this.$endTime, "change", this.timeChanged);
        this.$endDate
          .datepicker({format: 'mm-dd-yyyy'})
          .on("changeDate", this.dateChanged.bind(this));
      });
    }
  }
);
