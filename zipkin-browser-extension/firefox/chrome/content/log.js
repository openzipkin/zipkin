define([
        "firebug/lib/trace"
    ],
    function(FBTrace) {

        // Use log() to log instead of having checks for DBG_ZIPKIN everywhere.
        var log = function() {};
        try {
            if (FBTrace.DBG_ZIPKIN) {
                log = function(m) { FBTrace.sysout("zipkin; " + m); };
            }
        } catch(e) {}

        return log;
    }
);
