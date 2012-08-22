/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var Zipkin = Zipkin || {};

/*
 * Configuration variables for Zipkin UI
 */
Zipkin.Config = {

  /* Responsive grid */
  MAX_WINDOW_SIZE                : 1200,
  MAX_GRAPHIC_WIDTH              : 940,
  MIN_GRAPHIC_WIDTH              : 720,

  MAX_AGG_WINDOW_SIZE            : 1300,
  MAX_AGG_GRAPHIC_WIDTH          : 1300,
  MIN_AGG_GRAPHIC_WIDTH              : 720,

  /* d3 Visualization variables */
  MIN_SPANS_TO_FILTER            : 40, /* Minimum number of spans in trace to enable filtering */
  FILTER_SPAN_DURATION_THRESHOLD : 10, /* Filter span if duration is less than this (in ms) */

  /* Onebox */
  ONEBOX: {
    KEYS: {
      "memcached.keys": 1
    }
  },

  name: "Zipkin"
};
