define([
    "zipkin/log"
],
function(log) {
    var t = Components.classes["@mozilla.org/timer;1"], timers = [];
    function removeArrayItem(arr, it) {
        for (var i = arr.length-1; i >= 0; i--) {
            if (arr[i] == it) {
                return arr.splice(i, 1);
            }
        }
    }
    return {
        setTimeout: function(callback, timeout) {
            var timer = t.createInstance(Components.interfaces.nsITimer);
            timer.initWithCallback({ notify: function(timer) {
                callback();
                removeArrayItem(timers, timer);
            }}, timeout, Components.interfaces.nsITimer.TYPE_ONE_SHOT);
            timers.push(timer);
            return timer;
        },
        setInterval: function(callback, timeout) {
            var timer = t.createInstance(Components.interfaces.nsITimer);
            timer.initWithCallback({ notify: function(timer) {
                callback();
            }}, timeout, Components.interfaces.nsITimer.TYPE_REPEATING_SLACK);
            timers.push(timer);
            return timer;
        },
        clearTimeout: function(timer) {
            timer.cancel();
            removeArrayItem(timers, timer);
        },
        clearAllTimers: function() {
            for (var i = timers.length-1; i >= 0; i--) {
                this.clearTimeout(timers[i]);
            }
        }
    };
});