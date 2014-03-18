define([
        "zipkin/log"
    ],
    function(log) {
        /**
         * Information about a request traced by Zipkin.
         */
        function Trace(traceID, requestURL, referer) {
            this.traceID = traceID;
            this.requestURL = requestURL;
            this.referer = referer;
            this.timestamp = Date.now();
            this.visualization = '';
            this.rendered = false;
        }

        /**
         * Allows iterating over an array without accessing that array directly.
         *
         * The typical pattern is:
         *
         *  var it = new ArrayIterator(arrayToIterate), n;
         *  while (n = it.next()) {
         *    // Do something with array item n
         *  }
         */
        function ArrayIterator(arr) {
            var i = 0;
            var data = arr;
            // We don't need bounds checking because out-of-bounds access just returns undefined with no warnings.
            this.next = function() {
                return data[i++];
            };
        }

        // Stores the trace information
        var data = [];

        /**
         * Manage traces.
         */
        return new (function Traces() {
            // Add a new trace.
            this.add = function(traceID, requestURL, referer) {
                // Store new data
                data.unshift(new Trace(traceID, requestURL, referer));
                // Pop old data
                if (data.length > 100) {
                    var cutoff = Date.now() - 900000; // 15 minutes ago
                    for (var i = data.length-1; i >= 100; i--) {
                        if (data[i].timestamp < cutoff) {
                            data.splice(i, 1);
                        }
                    }
                }
                // Refresh visual listing of data
                if (typeof Firebug.MyZipkinPanel !== 'undefined') {
                    Firebug.MyZipkinPanel.refresh();
                }
                else {
                    log("The Zipkin panel has not yet been initialized.");
                }
            };
            // Clear all traces.
            this.clear = function() {
                data = [];
            };
            // Whether there have been any traces
            this.isEmpty = function() {
                return !data.length;
            };
            // Iterate over the data.
            this.getIterator = function() {
                return new ArrayIterator(data);
            };
        });
    }
);
