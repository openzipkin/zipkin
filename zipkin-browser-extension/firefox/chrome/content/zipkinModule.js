define([
        "firebug/lib/object",
        "firebug/lib/trace"
    ],
    function(Obj, FBTrace) {
        Firebug.Zipkin = Obj.extend(Firebug.Module, {
            initialize: function(owner) {
                Firebug.Module.initialize.apply(this, arguments);

                // Module initialization (there is one module instance per browser window)

                if (FBTrace.DBG_ZIPKIN) {
                    FBTrace.sysout("zipkin; Zipkin.initialize module");
                }
            },

            shutdown: function() {
                Firebug.Module.shutdown.apply(this, arguments);

                if (FBTrace.DBG_ZIPKIN) {
                    FBTrace.sysout("zipkin; Zipkin.shutdown module");
                }
            }
        });

        Firebug.registerModule(Firebug.Zipkin);

        return Firebug.Zipkin;
    }
);