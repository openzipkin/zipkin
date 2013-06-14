define([
        "firebug/lib/trace"
    ],
    function(FBTrace) {
        var maxID = Math.pow(2, 63)-1;

        function getRandIntBetween(lo, hi) {
            // Make sure lo <= hi
            if (lo > hi) {
                var t = lo;
                lo = hi;
                hi = t;
            }
            // Make sure lo and hi are integers
            lo = Math.ceil(lo);
            hi = Math.floor(hi);

            return Math.floor(Math.random()*(hi-lo+1)+lo);
        };

        // Get a random 64-bit int as a hex string.
        return function() {
            return getRandIntBetween(0, maxID).toString(16);
        };
    }
);
