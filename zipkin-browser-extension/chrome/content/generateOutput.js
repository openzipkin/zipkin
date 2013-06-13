define([
        "zipkin/log",
        "zipkin/translate",
        "zipkin/options",
        "zipkin/traces",
        "zipkin/http",
        "zipkin/timer"
    ],
    function(log, t, Options, Traces, HTTP, Timer) {
        // Pads a string to a given length, e.g. pad(12, 4, 0) -> '0012'
        function pad(n, d, p) {
            n += '';
            while (n.length < d) {
                n = p+n;
            }
            return n;
        }

        // 6/5/13 - 3:40:02pm PDT
        function msToTime(ms) {
            var d = new Date(ms);
            var h = d.getHours(), m = h < 12 ? 'am' : 'pm';
            h = h % 12;
            if (h == 0) h = 12;
            var tz = '';
            try { tz = ' ' + d.toString().match(/\(([A-Z]+)\)/)[1]; } catch(e) {}
            return (d.getMonth()+1) + '/' +
                d.getDate() + '/' +
                (d.getFullYear()+'').substring(2, 4) + ' - ' +
                h + ':' +
                pad(d.getMinutes(), 2, 0) + ':' +
                pad(d.getSeconds(), 2, 0) +
                m + tz;
        };

        // Track timers across refreshes and don't repeat the same requests.
        var timers = {};

        return function(panelNode) {
            var zipkin_base_url = Options.zipkin_site + '/traces/',
                zipkin_api_url = Options.zipkin_site + '/api/get/';

            // Output the trace information.
            var output = '<div class="zipkin-empty">' + t("empty") + '</div>';
            if (!Traces.isEmpty()) {
                output = '<table id="zipkin-data"><thead><tr><th>' + t("time") + '</th><th>' + t("traceid") + '</th><th>' + t("zipkinurl") + '</th><th>' + t("requesturl") + '</th><th>' + t("parenturl") + '</th></tr></thead><tbody>';
                var it = Traces.getIterator(), n, i = 0;
                while (n = it.next()) {
                    var zebra = ++i % 2 == 0 ? 'even' : 'odd';
                    output += '<tr id="zipkin-trace-' + n.traceID + '" class="zipkin-info ' + zebra + '"><td>' + msToTime(n.timestamp) + '</td><td>' + n.traceID + '</td><td><a href="' + zipkin_base_url + n.traceID + '" target="_blank">' + zipkin_base_url + n.traceID + '</a></td><td>' + n.requestURL + '</td><td>' + n.referer + '</td></tr>';
                    // If waterfalls are collapsed, hide them completely.
                    if (Options.collapse_waterfalls) {
                        continue;
                    }
                    // Use the cached visualization if possible.
                    if (n.visualization) {
                        output += n.visualization;
                        continue;
                    }
                    // Request the information we need to build the visualization.
                    // Wrapped in a closure so we can capture the information we need per iteration.
                    (function(traceID, traceObj) {
                        var zau = zipkin_api_url + traceID;
                        // If an HTTP request for this trace has already fired, abort;
                        // that request's callback will render the visualization we want.
                        if (typeof timers[zau] !== 'undefined') {
                            return;
                        }
                        timers[zau] = Timer.setTimeout(function() {
                            log("Timer callback fired!");
                            // Once the timer has fired, we're okay with sending off another request.
                            // We just don't want to send off a bunch of API requests that are
                            // due to new requests from the page causing the panel to refresh
                            // since this will cause duplicate trace visualizations
                            // (and way too many requests).
                            delete timers[zau];
                            try {
                                // Request span information from the Zipkin REST API.
                                HTTP.request(zau, function(data) {
                                    log("HTTP request returned!");
                                    try {
                                        data = JSON.parse(data);
                                    }
                                    catch(e) {
                                        log("Could not parse trace data response for URL " + url + ". This resource may not be integrated with Zipkin. Error: " + e);
                                    }
                                    // Render the traces.
                                    var spans = data.trace.spans.sort(function(a, b) {
                                        return b.duration - a.duration;
                                    }).slice(0, 10).sort(function(a, b) {
                                        return a.startTimestamp - b.startTimestamp;
                                    });
                                    var min = Infinity, max = 0;
                                    for (var i = 0, l = spans.length; i < l; i++) {
                                        var s = spans[i];
                                        if (s.startTimestamp < min) {
                                            min = s.startTimestamp;
                                        }
                                        if (s.startTimestamp + s.duration > max) {
                                            max = s.startTimestamp + s.duration;
                                        }
                                    }
                                    function percentAcross(n) {
                                        return Math.round(((n - min) / (max - min)) * 100);
                                    }
                                    var hasTraceData = false;
                                    var out = '<tr class="zipkin-panel-viz"><td colspan="5" class="zipkin-panel-viz-wrapper">';
                                    for (var i = 0, l = spans.length; i < l; i++) {
                                        var s = spans[i], ts = s.startTimestamp;
                                        var left = percentAcross(ts), right = percentAcross(ts + s.duration);
                                        if (right - left > 0) {
                                            hasTraceData = true;
                                            out += '<div class="zipkin-panel-viz-span-wrapper"><div class="zipkin-panel-viz-span" data-span="' + s.services.join(', ') + '" style="margin-left: ' + left + '%; margin-right: ' + (100 - right) + '%;"></div></div>'
                                        }
                                    }
                                    out += '</td></tr>';
                                    try {
                                        // Only cache the results if we successfully retrieved data.
                                        if (hasTraceData) {
                                            traceObj.visualization = out;
                                        }
                                        else {
                                            out = out.replace('</td></tr>', '$&' + t('no_trace_data'));
                                        }
                                        var regex = new RegExp('<tr id="zipkin-trace-' + traceID + '".*?</tr>');
                                        panelNode.innerHTML = panelNode.innerHTML.replace(regex, '$&' + out);
                                    }
                                    catch(e) {
                                        log("Error rendering trace data: " + e);
                                    }
                                });
                            }
                            catch(e) {
                                log("Error calling HTTP.request(): " + e);
                            }
                        }, 5000);
                    })(n.traceID, n);
                }
                output += '</tbody></table>';
            }
            return output;
        };
    }
);
