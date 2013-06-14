define([], function () {
    var Cc = Components.classes, Ci = Components.interfaces, Cu = Components.utils;

    // Save changes to the inline options (Addons > Zipkin > Options)
    return {
        prefs: null, // Preferences service
        profile_site: "", // Regex of preference value
        zipkin_site: "", // Preference value
        enable_tracing: true, // Preference value
        trace_zipkin_ui: false, // Preference value
        collapse_waterfalls: false, // Preference value
        _interval: null, // Track the refresh interval so we can clear it on shutdown
        // Run on load to initialize preference values
        startup: function() {
            this.prefs = Cc["@mozilla.org/preferences-service;1"]
                .getService(Ci.nsIPrefService)
                .getBranch("extensions.zipkin.");
            this.prefs.QueryInterface(Ci.nsIPrefBranch2);
            this.prefs.addObserver("", this, false);
            this.profile_site = new RegExp(this.prefs.getCharPref("profile_site"));
            this.zipkin_site = this.prefs.getCharPref("zipkin_site");
            this.enable_tracing = this.prefs.getBoolPref("enable_tracing");
            this.trace_zipkin_ui = this.prefs.getBoolPref("trace_zipkin_ui");
            this.collapse_waterfalls = this.prefs.getBoolPref("collapse_waterfalls");
            this.refreshInformation();
            // Refresh every 10 minutes
            try {
                this._interval = window.setInterval(this.refreshInformation, 600000);
            }
            catch(e) {}
        },
        // Unregister observers
        shutdown: function() {
            try {
                window.clearInterval(this._interval);
            }
            catch(e) {}
            this._interval = null;
            this.prefs.removeObserver("", this);
        },
        // Invoked when preferences are changed
        observe: function(subject, topic, data) {
            // Only respond to the event we care about
            if (topic == "nsPref:changed") {
                // Update the preferences we're tracking
                if (data == "profile_site") {
                    this.profile_site = new RegExp(this.prefs.getCharPref("profile_site"));
                    this.refreshInformation();
                }
                else if (data == "zipkin_site") {
                    this.zipkin_site = this.prefs.getCharPref("zipkin_site");
                    this.refreshInformation();
                }
                else if (data == "enable_tracing") {
                    this.enable_tracing = this.prefs.getBoolPref("enable_tracing");
                    this.refreshInformation();
                }
                else if (data == "trace_zipkin_ui") {
                    this.trace_zipkin_ui = this.prefs.getBoolPref("trace_zipkin_ui");
                    this.refreshInformation();
                }
                else if (data == "collapse_waterfalls") {
                    this.collapse_waterfalls = this.prefs.getBoolPref("collapse_waterfalls");
                    this.refreshInformation();
                }
            }
        },
        // Utility
        toggleEnabled: function() {
            this.enable_tracing = !this.enable_tracing;
            this.prefs.setBoolPref("enable_tracing", this.enable_tracing);
        },
        // Utility
        toggleTraceZipkinUI: function() {
            this.trace_zipkin_ui = !this.trace_zipkin_ui;
            this.prefs.setBoolPref("trace_zipkin_ui", this.trace_zipkin_ui);
        },
        // Utility
        toggleCollapseWaterfalls: function() {
            this.collapse_waterfalls = !this.collapse_waterfalls;
            this.prefs.setBoolPref("collapse_waterfalls", this.collapse_waterfalls);
        },
        // Utility
        refreshInformation: function() {
            // Reset anything we're displaying related to the settings here.
        }
    };
});
