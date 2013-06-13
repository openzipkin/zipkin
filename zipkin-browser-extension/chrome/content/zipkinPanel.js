define([
        "firebug/lib/object",
        "zipkin/log",
        "zipkin/translate",
        "zipkin/generateOutput",
        "zipkin/options",
        "zipkin/traces",
        "zipkin/timer"
    ],
    function(Obj, log, t, buildPanel, Options, Traces, Timer) {
        Firebug.ZipkinPanel = function ZipkinPanel() {};
        Firebug.ZipkinPanel.prototype = Obj.extend(Firebug.Panel, {
            name: "zipkin",
            title: "Zipkin",

            initialize: function() {
                Firebug.Panel.initialize.apply(this, arguments);
                log("ZipkinPanel.initialize");
                this.refresh();

                // Provide a way that we can reference our panel later so we can update it!
                Firebug.MyZipkinPanel = this;
            },

            destroy: function(state) {
                Firebug.Panel.destroy.apply(this, arguments);
                delete Firebug.MyZipkinPanel;
                //Timer.clearAllTimers();
                log("ZipkinPanel has been destroy()d");
            },

            show: function(state) {
                Firebug.Panel.show.apply(this, arguments);
            },

            refresh: function() {
                log("Refreshing Zipkin panel");
                this.panelNode.innerHTML = buildPanel(this.panelNode);
            },
            clear: function() {
                Traces.clear();
                this.refresh();
            },
            getOptionsMenuItems: function(context) {
                return [{
                    label: t("enable_tracing"),
                    nol10n: true,
                    type: "checkbox",
                    checked: Options.enable_tracing,
                    command: function() {
                        Options.toggleEnabled();
                    }
                }, {
                    label: t("collapse_waterfalls"),
                    nol10n: true,
                    type: "checkbox",
                    checked: Options.collapse_waterfalls,
                    command: function() {
                        Options.toggleCollapseWaterfalls();
                        Firebug.MyZipkinPanel.refresh();
                    }
                }, {
                    label: t("trace_zipkin_ui"),
                    nol10n: true,
                    type: "checkbox",
                    checked: Options.trace_zipkin_ui,
                    command: function() {
                        Options.toggleTraceZipkinUI();
                    }
                }, "-", {
                    label: t("clear_traces"),
                    nol10n: true,
                    type: "",
                    command: function() {
                        Firebug.MyZipkinPanel.clear();
                    }
                }];
            }
        });

        Firebug.registerPanel(Firebug.ZipkinPanel);
        Firebug.registerStylesheet("chrome://zipkin/skin/zipkin.css");

        log("ZipkinPanel has finished loading");

        return Firebug.ZipkinPanel;
    }
);
