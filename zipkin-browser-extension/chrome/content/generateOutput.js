define([
        "zipkin/log",
        "zipkin/translate",
        "zipkin/options",
        "zipkin/traces"
    ],
    function(log, t, Options, Traces) {
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

        return function() {
            // The API uses /api/get/ instead of /traces/
            var zipkin_base_url = Options.zipkin_site + '/traces/';

            // Output the trace information.
            var output = '<div class="zipkin-empty">' + t("empty") + '</div>';
            if (!Traces.isEmpty()) {
                output = '<table id="zipkin-data"><thead><tr><th>' + t("time") + '</th><th>' + t("traceid") + '</th><th>' + t("zipkinurl") + '</th><th>' + t("requesturl") + '</th><th>' + t("parenturl") + '</th></tr></thead><tbody>';
                var it = Traces.getIterator(), n;
                while (n = it.next()) {
                    output += '<tr><td>' + msToTime(n.timestamp) + '</td><td>' + n.traceID + '</td><td><a href="' + zipkin_base_url + n.traceID + '" target="_blank">' + zipkin_base_url + n.traceID + '</a></td><td>' + n.requestURL + '</td><td>' + n.referer + '</td></tr>';
                }
                output += '</tbody></table>';
            }
            return output;
        };
    }
);
