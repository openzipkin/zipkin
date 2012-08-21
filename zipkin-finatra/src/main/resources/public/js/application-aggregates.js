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
/*global root_url:false */
//= require zipkin
var Zipkin = Zipkin || {};
Zipkin.Application = Zipkin.Application || {};
Zipkin.Application.Aggregates = (function() {
  var templatize = Zipkin.Util.templatize
    , TEMPLATES = Zipkin.Util.TEMPLATES
    ;

  var initialize = function () {
    Zipkin.Base.initialize();

    var getDependencyTree = function () {
      // Show some loading stuff while we wait for the query
      $('#help-msg').hide();
      $('#error-box').hide();
      $('#loading-data').show();

      var query_data = {
        adjust_clock_skew: Zipkin.Base.clockSkewState() ? 'true' : 'false',
      };

//      $.ajax({
//        type: 'GET',
//        url: root_url + "aggregates/dependency_tree",
////        url: root_url + "api/get/" + traceId,
//        data: query_data,
//        success: getTraceSuccess(traceId),
////        success: getTraceSuccess(traceId),
//        error: function(xhr, status, error) {
//          $('#trace-content').hide();
//          $('#loading-data').hide();
//          $('#error-msg').text(error);
//          $('.error-box').show();
//
//        }
      Zipkin.GetDependencyTree.initialize();
    };
    getDependencyTree();
  };

  return {
    initialize: initialize
//    getTraceSuccess: getTraceSuccess
  };
})();

Zipkin.GetDependencyTree = (function() {
  var templatize = Zipkin.Util.templatize
    , TEMPLATES = Zipkin.Util.TEMPLATES
    , autocompleteTerms = []
    ;

  var initialize = function() {
    DependencyTree._init();
  };

  var DependencyTree = (function DependencyTree() {

    return {
      _init: function() {

        /* TraceDependency */
        var linkList = [
            {source: "support.twitter.com-ssl", target: "cluster_support", depth: 2, count: 3},
            {source: "Ibis", target: "kestrel", depth: 2, count: 27},
            {source: "tweetdeck.com-ssl", target: "cluster_tweetdeck_web_app", depth: 2, count: 43},
            {source: "api.twitter.com-plaintext", target: "cluster_tweet_button", depth: 2, count: 4},
            {source: "api.twitter.com-ssl", target: "macaw.woodstar", depth: 2, count: 3},
            {source: "api.twitter.com-plaintext", target: "macaw.woodstar", depth: 2, count: 4},
            {source: "api.twitter.com-plaintext", target: "cluster_macaw_swift", depth: 2, count: 3},
            {source: "api.twitter.com-plaintext", target: "Limiter", depth: 2, count: 4},
            {source: "api.twitter.com-ssl", target: "Limiter", depth: 2, count: 5},
            {source: "api.twitter.com-plaintext", target: "macaw.rufous", depth: 2, count: 6},
            {source: "s.twitter.com-ssl", target: "cluster_search_api", depth: 2, count: 20},
            {source: "api.twitter.com-plaintext", target: "cluster_search_api", depth: 2, count: 464},
            {source: "api.twitter.com-plaintext", target: "cluster_talon", depth: 2, count: 30},
            {source: "api.twitter.com-plaintext", target: "cluster_woodstar", depth: 2, count: 923},
            {source: "api.twitter.com-ssl", target: "cluster_woodstar", depth: 2, count: 1093},
            {source: "api.twitter.com-ssl", target: "limiter", depth: 2, count: 491},
            {source: "api.twitter.com-plaintext", target: "limiter", depth: 2, count: 579},
            {source: "macaw.syndication", target: "syndication-thrift", depth: 2, count: 8},
            {source: "macaw.syndication", target: "SyndicationService", depth: 2, count: 33},
            {source: "twitter.com-ssl", target: "passbird", depth: 2, count: 37},
            {source: "api.twitter.com-plaintext", target: "passbird", depth: 2, count: 618},
            {source: "api.twitter.com-ssl", target: "passbird", depth: 2, count: 1178},
            {source: "twitter.com-ssl", target: "cluster_twitterweb_40_unicorn", depth: 2, count: 18},
            {source: "api.twitter.com-plaintext", target: "cluster_twitterweb_40_unicorn", depth: 2, count: 26},
            {source: "api.twitter.com-ssl", target: "cluster_twitterweb_40_unicorn", depth: 2, count: 28},
            {source: "s.twitter.com-ssl", target: "monorail", depth: 2, count: 30},
            {source: "twitter.com-ssl", target: "monorail", depth: 2, count: 1129},
            {source: "api.twitter.com-plaintext", target: "monorail", depth: 2, count: 1217},
            {source: "api.twitter.com-ssl", target: "monorail", depth: 2, count: 3089},
            {source: "api.twitter.com-ssl", target: "macaw.typeahead", depth: 2, count: 1},
            {source: "twitter.com-ssl", target: "macaw.typeahead", depth: 2, count: 2},
            {source: "s.twitter.com-ssl", target: "cluster_twitterweb_unicorn", depth: 2, count: 2},
            {source: "twitter.com-ssl", target: "cluster_twitterweb_unicorn", depth: 2, count: 76},
            {source: "api.twitter.com-plaintext", target: "cluster_twitterweb_unicorn", depth: 2, count: 85},
            {source: "api.twitter.com-ssl", target: "cluster_twitterweb_unicorn", depth: 2, count: 90},
            {source: "api.twitter.com-plaintext", target: "cluster_macaw_typeahead", depth: 2, count: 2},
            {source: "twitter.com-ssl", target: "cluster_macaw_typeahead", depth: 2, count: 38},
            {source: "api.twitter.com-ssl", target: "cluster_macaw_typeahead", depth: 2, count: 44},
            {source: "r.twimg.com-ssl", target: "cluster_rufous", depth: 2, count: 50},
            {source: "api.twitter.com-plaintext", target: "cluster_rufous", depth: 2, count: 1267},
            {source: "api.twitter.com-plaintext", target: "cluster_mobile", depth: 2, count: 15},
            {source: "mobile.twitter.com-ssl", target: "cluster_mobile", depth: 2, count: 130},
            {source: "peacock", target: "mysql/twttr_production", depth: 2, count: 61935},
            {source: "peacock", target: "rainbird.query", depth: 2, count: 3033},
            {source: "peacock", target: "mysql/revss_production", depth: 2, count: 19815},
            {source: "peacock", target: "mysql/ads_insight", depth: 2, count: 329},
            {source: "peacock", target: "mysql/revenue_insight", depth: 2, count: 422},
            {source: "peacock", target: "tls", depth: 2, count: 205},
            {source: "image-proxy-origin.twimg.com-ssl", target: "cluster_image_proxy_origin", depth: 2, count: 7},
            {source: "cluster_woodstar", target: "adserver", depth: 3, count: 4},
            {source: "macaw.woodstar", target: "adserver", depth: 3, count: 120},
            {source: "cluster_woodstar", target: "timelineservice", depth: 3, count: 55},
            {source: "macaw.woodstar", target: "timelineservice", depth: 3, count: 1229},
            {source: "monorail", target: "promptbird", depth: 3, count: 16},
            {source: "api.twitter.com-plaintext", target: "TimelineService", depth: 3, count: 2},
            {source: "api.twitter.com-ssl", target: "TimelineService", depth: 3, count: 4},
            {source: "twitter.com-ssl", target: "TimelineService", depth: 3, count: 6},
            {source: "monorail", target: "TimelineService", depth: 3, count: 9},
            {source: "macaw.woodstar", target: "TimelineService", depth: 3, count: 11},
            {source: "cluster_woodstar", target: "TimelineService", depth: 3, count: 54},
            {source: "monorail", target: "mysql/client_application_images", depth: 3, count: 10},
            {source: "monorail", target: "flock/lowQoS", depth: 3, count: 4},
            {source: "cluster_twitterweb_unicorn", target: "timeline_service", depth: 3, count: 2},
            {source: "monorail", target: "timeline_service", depth: 3, count: 2690},
            {source: "TimelineService", target: "mysql/erasures", depth: 4, count: 4},
            {source: "monorail", target: "mysql/erasures", depth: 4, count: 20},
            {source: "twitter.com-ssl", target: "tweetypie", depth: 4, count: 1},
            {source: "cluster_twitterweb_unicorn", target: "tweetypie", depth: 4, count: 1},
            {source: "api.twitter.com-plaintext", target: "tweetypie", depth: 4, count: 1},
            {source: "timelineservice", target: "tweetypie", depth: 4, count: 1},
            {source: "tweetypie.forked", target: "tweetypie", depth: 4, count: 3},
            {source: "api.twitter.com-ssl", target: "tweetypie", depth: 4, count: 5},
            {source: "cluster_search_api", target: "tweetypie", depth: 4, count: 9},
            {source: "TimelineService", target: "tweetypie", depth: 4, count: 32},
            {source: "cluster_woodstar", target: "tweetypie", depth: 4, count: 69},
            {source: "monorail", target: "tweetypie", depth: 4, count: 354},
            {source: "peacock", target: "tweetypie", depth: 4, count: 936},
            {source: "macaw.woodstar", target: "tweetypie", depth: 4, count: 1014},
            {source: "TimelineService", target: "metastore", depth: 4, count: 18},
            {source: "monorail", target: "metastore", depth: 4, count: 22},
            {source: "TimelineService", target: "mysql/phone_verification_call_logs", depth: 4, count: 4},
            {source: "cluster_twitterweb_unicorn", target: "mysql/password_seeds", depth: 4, count: 2},
            {source: "TimelineService", target: "mysql/password_seeds", depth: 4, count: 2},
            {source: "monorail", target: "mysql/password_seeds", depth: 4, count: 243},
            {source: "TimelineService", target: "groups", depth: 4, count: 1},
            {source: "api.twitter.com-plaintext", target: "groups", depth: 4, count: 1},
            {source: "api.twitter.com-ssl", target: "groups", depth: 4, count: 6},
            {source: "monorail", target: "groups", depth: 4, count: 2604},
            {source: "tweetypie", target: "mysql/blocks", depth: 5, count: 2},
            {source: "monorail", target: "mysql/blocks", depth: 5, count: 35},
            {source: "tweetypie", target: "urlStoreService", depth: 5, count: 1},
            {source: "tweetypie", target: "mysql/verified_profile_requests", depth: 5, count: 7},
            {source: "TimelineService", target: "mysql/verified_profile_requests", depth: 5, count: 8},
            {source: "monorail", target: "mysql/verified_profile_requests", depth: 5, count: 45},
            {source: "TimelineService", target: "memcached/trends", depth: 5, count: 2},
            {source: "cluster_twitterweb_unicorn", target: "memcached/trends", depth: 5, count: 4},
            {source: "tweetypie", target: "memcached/trends", depth: 5, count: 113},
            {source: "monorail", target: "memcached/trends", depth: 5, count: 22014},
            {source: "tweetypie", target: "mysql/client_applications", depth: 5, count: 2},
            {source: "cluster_twitterweb_unicorn", target: "mysql/client_applications", depth: 5, count: 3},
            {source: "TimelineService", target: "mysql/client_applications", depth: 5, count: 5},
            {source: "monorail", target: "mysql/client_applications", depth: 5, count: 424},
            {source: "TimelineService", target: "penguin", depth: 5, count: 3},
            {source: "monorail", target: "penguin", depth: 5, count: 9},
            {source: "tweetypie", target: "penguin", depth: 5, count: 62},
            {source: "tweetypie", target: "com.twitter.service.spiderduck.gen.UrlStoreService", depth: 5, count: 1},
            {source: "TimelineService", target: "talon", depth: 5, count: 1},
            {source: "monorail", target: "talon", depth: 5, count: 13},
            {source: "tweetypie", target: "talon", depth: 5, count: 15},
            {source: "tweetypie", target: "timelineService", depth: 5, count: 21},
            {source: "tweetypie", target: "haplo3/lowQoS", depth: 5, count: 4},
            {source: "TimelineService", target: "haplo3/lowQoS", depth: 5, count: 15},
            {source: "monorail", target: "haplo3/lowQoS", depth: 5, count: 94},
            {source: "tweetypie", target: "expandodoService", depth: 5, count: 32},
            {source: "tweetypie", target: "mysql/user_tattles", depth: 5, count: 8},
            {source: "monorail", target: "mysql/user_tattles", depth: 5, count: 70},
            {source: "groups", target: "gizmoduck", depth: 5, count: 2},
            {source: "promptbird", target: "gizmoduck", depth: 5, count: 6},
            {source: "cluster_macaw_typeahead", target: "gizmoduck", depth: 5, count: 7},
            {source: "macaw.typeahead", target: "gizmoduck", depth: 5, count: 49},
            {source: "TimelineService", target: "gizmoduck", depth: 5, count: 95},
            {source: "cluster_woodstar", target: "gizmoduck", depth: 5, count: 197},
            {source: "tweetypie", target: "gizmoduck", depth: 5, count: 254},
            {source: "monorail", target: "gizmoduck", depth: 5, count: 465},
            {source: "macaw.woodstar", target: "gizmoduck", depth: 5, count: 2239},
            {source: "peacock", target: "gizmoduck", depth: 5, count: 17566},
            {source: "cluster_twitterweb_40_unicorn", target: "memcached/page", depth: 5, count: 23},
            {source: "cluster_twitterweb_unicorn", target: "memcached/page", depth: 5, count: 86},
            {source: "groups", target: "memcached/page", depth: 5, count: 419},
            {source: "TimelineService", target: "memcached/page", depth: 5, count: 683},
            {source: "tweetypie", target: "memcached/page", depth: 5, count: 2609},
            {source: "monorail", target: "memcached/page", depth: 5, count: 54669},
            {source: "groups", target: "haplo/lowQoS", depth: 5, count: 1},
            {source: "tweetypie", target: "haplo/lowQoS", depth: 5, count: 6},
            {source: "TimelineService", target: "haplo/lowQoS", depth: 5, count: 13},
            {source: "monorail", target: "haplo/lowQoS", depth: 5, count: 43},
            {source: "groups", target: "mysql/contributor_permissions", depth: 5, count: 5},
            {source: "TimelineService", target: "mysql/contributor_permissions", depth: 5, count: 6},
            {source: "monorail", target: "mysql/contributor_permissions", depth: 5, count: 447},
            {source: "cluster_twitterweb_40_unicorn", target: "mysql/device_sources", depth: 5, count: 2},
            {source: "cluster_twitterweb_unicorn", target: "mysql/device_sources", depth: 5, count: 16},
            {source: "groups", target: "mysql/device_sources", depth: 5, count: 117},
            {source: "TimelineService", target: "mysql/device_sources", depth: 5, count: 255},
            {source: "tweetypie", target: "mysql/device_sources", depth: 5, count: 2565},
            {source: "monorail", target: "mysql/device_sources", depth: 5, count: 32207},
            {source: "cluster_twitterweb_unicorn", target: "mysql/saved_searches", depth: 5, count: 1},
            {source: "tweetypie", target: "mysql/saved_searches", depth: 5, count: 5},
            {source: "groups", target: "mysql/saved_searches", depth: 5, count: 6},
            {source: "TimelineService", target: "mysql/saved_searches", depth: 5, count: 11},
            {source: "monorail", target: "mysql/saved_searches", depth: 5, count: 1142},
            {source: "promptbird", target: "memcached/fragment", depth: 5, count: 54},
            {source: "cluster_twitterweb_40_unicorn", target: "memcached/fragment", depth: 5, count: 124},
            {source: "cluster_twitterweb_unicorn", target: "memcached/fragment", depth: 5, count: 365},
            {source: "TimelineService", target: "memcached/fragment", depth: 5, count: 22723},
            {source: "groups", target: "memcached/fragment", depth: 5, count: 40864},
            {source: "tweetypie", target: "memcached/fragment", depth: 5, count: 137701},
            {source: "monorail", target: "memcached/fragment", depth: 5, count: 2218633},
            {source: "cluster_twitterweb_unicorn", target: "mysql/brand_banners", depth: 5, count: 2},
            {source: "TimelineService", target: "mysql/brand_banners", depth: 5, count: 63},
            {source: "groups", target: "mysql/brand_banners", depth: 5, count: 391},
            {source: "tweetypie", target: "mysql/brand_banners", depth: 5, count: 561},
            {source: "monorail", target: "mysql/brand_banners", depth: 5, count: 5352},
            {source: "cluster_twitterweb_40_unicorn", target: "memcached/activity", depth: 5, count: 7},
            {source: "cluster_twitterweb_unicorn", target: "memcached/activity", depth: 5, count: 12},
            {source: "groups", target: "memcached/activity", depth: 5, count: 221},
            {source: "TimelineService", target: "memcached/activity", depth: 5, count: 485},
            {source: "tweetypie", target: "memcached/activity", depth: 5, count: 2353},
            {source: "monorail", target: "memcached/activity", depth: 5, count: 46505},
            {source: "timeline_service", target: "flock/highQoS", depth: 5, count: 2},
            {source: "groups", target: "flock/highQoS", depth: 5, count: 5},
            {source: "timelineservice", target: "flock/highQoS", depth: 5, count: 55},
            {source: "tweetypie", target: "flock/highQoS", depth: 5, count: 164},
            {source: "monorail", target: "flock/highQoS", depth: 5, count: 694},
            {source: "TimelineService", target: "flock/highQoS", depth: 5, count: 1161},
            {source: "tweetypie", target: "mysql/profile_background_images", depth: 5, count: 157},
            {source: "TimelineService", target: "mysql/profile_background_images", depth: 5, count: 185},
            {source: "groups", target: "mysql/profile_background_images", depth: 5, count: 218},
            {source: "monorail", target: "mysql/profile_background_images", depth: 5, count: 3422},
            {source: "groups", target: "tbird", depth: 5, count: 2},
            {source: "monorail", target: "tbird", depth: 5, count: 46},
            {source: "tweetypie", target: "tbird", depth: 5, count: 47},
            {source: "TimelineService", target: "tbird", depth: 5, count: 52},
            {source: "cluster_twitterweb_unicorn", target: "haplo/highQoS", depth: 5, count: 4},
            {source: "timeline_service", target: "haplo/highQoS", depth: 5, count: 18},
            {source: "timelineservice", target: "haplo/highQoS", depth: 5, count: 34},
            {source: "tls", target: "haplo/highQoS", depth: 5, count: 39},
            {source: "groups", target: "haplo/highQoS", depth: 5, count: 80},
            {source: "tweetypie", target: "haplo/highQoS", depth: 5, count: 395},
            {source: "TimelineService", target: "haplo/highQoS", depth: 5, count: 2557},
            {source: "monorail", target: "haplo/highQoS", depth: 5, count: 6117},
            {source: "cluster_twitterweb_40_unicorn", target: "mysql/facebook_connections", depth: 5, count: 1},
            {source: "promptbird", target: "mysql/facebook_connections", depth: 5, count: 6},
            {source: "cluster_twitterweb_unicorn", target: "mysql/facebook_connections", depth: 5, count: 6},
            {source: "tweetypie", target: "mysql/facebook_connections", depth: 5, count: 13},
            {source: "groups", target: "mysql/facebook_connections", depth: 5, count: 212},
            {source: "TimelineService", target: "mysql/facebook_connections", depth: 5, count: 291},
            {source: "monorail", target: "mysql/facebook_connections", depth: 5, count: 18535},
            {source: "groups", target: "photurkey", depth: 5, count: 1},
            {source: "tweetypie", target: "photurkey", depth: 5, count: 26},
            {source: "TimelineService", target: "photurkey", depth: 5, count: 34},
            {source: "monorail", target: "photurkey", depth: 5, count: 172},
            {source: "groups", target: "mysql/promptbird_campaigns", depth: 5, count: 1},
            {source: "monorail", target: "mysql/promptbird_campaigns", depth: 5, count: 5},
            {source: "cluster_twitterweb_unicorn", target: "mysql/promptbird_dismissals", depth: 5, count: 1},
            {source: "TimelineService", target: "mysql/promptbird_dismissals", depth: 5, count: 2},
            {source: "groups", target: "mysql/promptbird_dismissals", depth: 5, count: 2},
            {source: "monorail", target: "mysql/promptbird_dismissals", depth: 5, count: 282},
            {source: "tweetypie", target: "mysql/client_applications_xtra", depth: 5, count: 2},
            {source: "groups", target: "mysql/client_applications_xtra", depth: 5, count: 2},
            {source: "TimelineService", target: "mysql/client_applications_xtra", depth: 5, count: 5},
            {source: "monorail", target: "mysql/client_applications_xtra", depth: 5, count: 362},
            {source: "com.twitter.service.spiderduck.gen.UrlStoreService", target: "thumbingbird_proxy_thrift", depth: 6, count: 1},
            {source: "cuckoo-client", target: "client", depth: 6, count: 1},
            {source: "gazetteer-buffered", target: "client", depth: 6, count: 12},
            {source: "com.twitter.service.spiderduck.gen.UrlStoreService", target: "client", depth: 6, count: 18},
            {source: "cluster_rufous", target: "client", depth: 6, count: 396},
            {source: "macaw.rufous", target: "client", depth: 6, count: 1116},
            {source: "com.twitter.service.spiderduck.gen.UrlStoreService", target: "image_proxy", depth: 6, count: 17},
            {source: "timelineService", target: "haplo3/highQoS", depth: 6, count: 1},
            {source: "cluster_twitterweb_unicorn", target: "haplo3/highQoS", depth: 6, count: 2},
            {source: "cluster_twitterweb_40_unicorn", target: "haplo3/highQoS", depth: 6, count: 3},
            {source: "timeline_service", target: "haplo3/highQoS", depth: 6, count: 12},
            {source: "timelineservice", target: "haplo3/highQoS", depth: 6, count: 19},
            {source: "groups", target: "haplo3/highQoS", depth: 6, count: 40},
            {source: "tweetypie", target: "haplo3/highQoS", depth: 6, count: 478},
            {source: "TimelineService", target: "haplo3/highQoS", depth: 6, count: 3643},
            {source: "monorail", target: "haplo3/highQoS", depth: 6, count: 5240},
            {source: "expandodoService", target: "manhattan", depth: 6, count: 1},
            {source: "com.twitter.service.spiderduck.gen.UrlStoreService", target: "manhattan", depth: 6, count: 61},
            {source: "timeline_service", target: "memcached/user", depth: 6, count: 3},
            {source: "promptbird", target: "memcached/user", depth: 6, count: 36},
            {source: "cluster_twitterweb_40_unicorn", target: "memcached/user", depth: 6, count: 413},
            {source: "cluster_twitterweb_unicorn", target: "memcached/user", depth: 6, count: 1548},
            {source: "TimelineService", target: "memcached/user", depth: 6, count: 64700},
            {source: "gizmoduck", target: "memcached/user", depth: 6, count: 88718},
            {source: "tweetypie", target: "memcached/user", depth: 6, count: 111907},
            {source: "groups", target: "memcached/user", depth: 6, count: 148901},
            {source: "monorail", target: "memcached/user", depth: 6, count: 2047821},
            {source: "promptbird", target: "flockdb_edges", depth: 6, count: 1},
            {source: "timeline_service", target: "flockdb_edges", depth: 6, count: 1},
            {source: "cluster_twitterweb_unicorn", target: "flockdb_edges", depth: 6, count: 4},
            {source: "macaw.woodstar", target: "flockdb_edges", depth: 6, count: 8},
            {source: "timelineservice", target: "flockdb_edges", depth: 6, count: 14},
            {source: "cluster_woodstar", target: "flockdb_edges", depth: 6, count: 15},
            {source: "gizmoduck", target: "flockdb_edges", depth: 6, count: 254},
            {source: "groups", target: "flockdb_edges", depth: 6, count: 485},
            {source: "TimelineService", target: "flockdb_edges", depth: 6, count: 500},
            {source: "tweetypie", target: "flockdb_edges", depth: 6, count: 1390},
            {source: "monorail", target: "flockdb_edges", depth: 6, count: 18138},
            {source: "cluster_twitterweb_40_unicorn", target: "mysql/role_users", depth: 6, count: 7},
            {source: "cluster_twitterweb_unicorn", target: "mysql/role_users", depth: 6, count: 31},
            {source: "gizmoduck", target: "mysql/role_users", depth: 6, count: 908},
            {source: "TimelineService", target: "mysql/role_users", depth: 6, count: 1330},
            {source: "groups", target: "mysql/role_users", depth: 6, count: 1852},
            {source: "tweetypie", target: "mysql/role_users", depth: 6, count: 4056},
            {source: "monorail", target: "mysql/role_users", depth: 6, count: 78145},
            {source: "cluster_twitterweb_unicorn", target: "mysql/none", depth: 6, count: 3},
            {source: "gizmoduck", target: "mysql/none", depth: 6, count: 7},
            {source: "timeline_service", target: "mysql/none", depth: 6, count: 15},
            {source: "groups", target: "mysql/none", depth: 6, count: 22},
            {source: "tweetypie", target: "mysql/none", depth: 6, count: 827},
            {source: "TimelineService", target: "mysql/none", depth: 6, count: 1684},
            {source: "monorail", target: "mysql/none", depth: 6, count: 10128},
            {source: "cluster_twitterweb_40_unicorn", target: "mysql/bouncers", depth: 6, count: 1},
            {source: "cluster_twitterweb_unicorn", target: "mysql/bouncers", depth: 6, count: 3},
            {source: "TimelineService", target: "mysql/bouncers", depth: 6, count: 106},
            {source: "groups", target: "mysql/bouncers", depth: 6, count: 115},
            {source: "tweetypie", target: "mysql/bouncers", depth: 6, count: 192},
            {source: "gizmoduck", target: "mysql/bouncers", depth: 6, count: 2722},
            {source: "monorail", target: "mysql/bouncers", depth: 6, count: 6677},
            {source: "timeline_service", target: "memcached/object", depth: 6, count: 6},
            {source: "promptbird", target: "memcached/object", depth: 6, count: 50},
            {source: "cluster_twitterweb_40_unicorn", target: "memcached/object", depth: 6, count: 301},
            {source: "cluster_twitterweb_unicorn", target: "memcached/object", depth: 6, count: 1191},
            {source: "TimelineService", target: "memcached/object", depth: 6, count: 7205},
            {source: "groups", target: "memcached/object", depth: 6, count: 8036},
            {source: "tweetypie", target: "memcached/object", depth: 6, count: 80435},
            {source: "gizmoduck", target: "memcached/object", depth: 6, count: 652521},
            {source: "monorail", target: "memcached/object", depth: 6, count: 1751197},
            {source: "timeline_service", target: "mysql/oauth_access_tokens_xtra", depth: 6, count: 1},
            {source: "gizmoduck", target: "mysql/oauth_access_tokens_xtra", depth: 6, count: 3},
            {source: "groups", target: "mysql/oauth_access_tokens_xtra", depth: 6, count: 9},
            {source: "TimelineService", target: "mysql/oauth_access_tokens_xtra", depth: 6, count: 53},
            {source: "tweetypie", target: "mysql/oauth_access_tokens_xtra", depth: 6, count: 161},
            {source: "monorail", target: "mysql/oauth_access_tokens_xtra", depth: 6, count: 1372},
            {source: "timeline_service", target: "memcached/united", depth: 6, count: 6},
            {source: "promptbird", target: "memcached/united", depth: 6, count: 94},
            {source: "cluster_twitterweb_40_unicorn", target: "memcached/united", depth: 6, count: 162},
            {source: "cluster_twitterweb_unicorn", target: "memcached/united", depth: 6, count: 572},
            {source: "gizmoduck", target: "memcached/united", depth: 6, count: 20314},
            {source: "groups", target: "memcached/united", depth: 6, count: 130811},
            {source: "TimelineService", target: "memcached/united", depth: 6, count: 212564},
            {source: "tweetypie", target: "memcached/united", depth: 6, count: 250877},
            {source: "monorail", target: "memcached/united", depth: 6, count: 2992494},
            {source: "cluster_twitterweb_unicorn", target: "mysql/apple_push_destinations", depth: 6, count: 5},
            {source: "groups", target: "mysql/apple_push_destinations", depth: 6, count: 19},
            {source: "tweetypie", target: "mysql/apple_push_destinations", depth: 6, count: 70},
            {source: "TimelineService", target: "mysql/apple_push_destinations", depth: 6, count: 106},
            {source: "gizmoduck", target: "mysql/apple_push_destinations", depth: 6, count: 546},
            {source: "monorail", target: "mysql/apple_push_destinations", depth: 6, count: 4363},
            {source: "timeline_service", target: "mysql/oauth_access_tokens", depth: 6, count: 1},
            {source: "gizmoduck", target: "mysql/oauth_access_tokens", depth: 6, count: 3},
            {source: "cluster_twitterweb_unicorn", target: "mysql/oauth_access_tokens", depth: 6, count: 3},
            {source: "groups", target: "mysql/oauth_access_tokens", depth: 6, count: 10},
            {source: "TimelineService", target: "mysql/oauth_access_tokens", depth: 6, count: 87},
            {source: "tweetypie", target: "mysql/oauth_access_tokens", depth: 6, count: 384},
            {source: "monorail", target: "mysql/oauth_access_tokens", depth: 6, count: 3522},
            {source: "timeline_service", target: "memcached/lookaside", depth: 6, count: 25},
            {source: "promptbird", target: "memcached/lookaside", depth: 6, count: 460},
            {source: "cluster_twitterweb_40_unicorn", target: "memcached/lookaside", depth: 6, count: 2073},
            {source: "cluster_twitterweb_unicorn", target: "memcached/lookaside", depth: 6, count: 7114},
            {source: "TimelineService", target: "memcached/lookaside", depth: 6, count: 292443},
            {source: "groups", target: "memcached/lookaside", depth: 6, count: 424269},
            {source: "gizmoduck", target: "memcached/lookaside", depth: 6, count: 1048081},
            {source: "tweetypie", target: "memcached/lookaside", depth: 6, count: 1196052},
            {source: "monorail", target: "memcached/lookaside", depth: 6, count: 23859142},
            {source: "groups", target: "mysql/profile_images", depth: 6, count: 2},
            {source: "timeline_service", target: "mysql/profile_images", depth: 6, count: 2},
            {source: "gizmoduck", target: "mysql/profile_images", depth: 6, count: 41},
            {source: "tweetypie", target: "mysql/profile_images", depth: 6, count: 46},
            {source: "TimelineService", target: "mysql/profile_images", depth: 6, count: 164},
            {source: "monorail", target: "mysql/profile_images", depth: 6, count: 2332},
            {source: "TimelineService", target: "mysql/roles", depth: 6, count: 2},
            {source: "gizmoduck", target: "mysql/roles", depth: 6, count: 3},
            {source: "groups", target: "mysql/roles", depth: 6, count: 4},
            {source: "monorail", target: "mysql/roles", depth: 6, count: 9},
            {source: "gizmoduck", target: "mysql/users", depth: 6, count: 3},
            {source: "cluster_twitterweb_40_unicorn", target: "mysql/users", depth: 6, count: 7},
            {source: "cluster_twitterweb_unicorn", target: "mysql/users", depth: 6, count: 12},
            {source: "TimelineService", target: "mysql/users", depth: 6, count: 166},
            {source: "groups", target: "mysql/users", depth: 6, count: 171},
            {source: "tweetypie", target: "mysql/users", depth: 6, count: 1273},
            {source: "monorail", target: "mysql/users", depth: 6, count: 24971},
            {source: "gizmoduck", target: "mysql/device_languages", depth: 6, count: 2},
            {source: "groups", target: "mysql/device_languages", depth: 6, count: 3},
            {source: "tweetypie", target: "mysql/device_languages", depth: 6, count: 56},
            {source: "TimelineService", target: "mysql/device_languages", depth: 6, count: 154},
            {source: "monorail", target: "mysql/device_languages", depth: 6, count: 399},
            {source: "groups", target: "mysql/devices", depth: 6, count: 12},
            {source: "TimelineService", target: "mysql/devices", depth: 6, count: 41},
            {source: "tweetypie", target: "mysql/devices", depth: 6, count: 138},
            {source: "gizmoduck", target: "mysql/devices", depth: 6, count: 478},
            {source: "monorail", target: "mysql/devices", depth: 6, count: 2825},
            {source: "gizmoduck", target: "mysql/invitations", depth: 6, count: 16},
            {source: "monorail", target: "mysql/invitations", depth: 6, count: 17},
            {source: "timeline_service", target: "memcached/counter", depth: 6, count: 18},
            {source: "promptbird", target: "memcached/counter", depth: 6, count: 201},
            {source: "cluster_twitterweb_40_unicorn", target: "memcached/counter", depth: 6, count: 263},
            {source: "cluster_twitterweb_unicorn", target: "memcached/counter", depth: 6, count: 848},
            {source: "gizmoduck", target: "memcached/counter", depth: 6, count: 81461},
            {source: "TimelineService", target: "memcached/counter", depth: 6, count: 129081},
            {source: "tweetypie", target: "memcached/counter", depth: 6, count: 374933},
            {source: "groups", target: "memcached/counter", depth: 6, count: 508157},
            {source: "monorail", target: "memcached/counter", depth: 6, count: 5863693},
            {source: "cluster_twitterweb_40_unicorn", target: "memcached/rate", depth: 6, count: 260},
            {source: "cluster_twitterweb_unicorn", target: "memcached/rate", depth: 6, count: 1052},
            {source: "groups", target: "memcached/rate", depth: 6, count: 1508},
            {source: "TimelineService", target: "memcached/rate", depth: 6, count: 2364},
            {source: "tweetypie", target: "memcached/rate", depth: 6, count: 34334},
            {source: "gizmoduck", target: "memcached/rate", depth: 6, count: 46427},
            {source: "monorail", target: "memcached/rate", depth: 6, count: 700027},
            {source: "photurkey", target: "HTTP_client_blobstore_metadata", depth: 6, count: 12},
            {source: "photurkey", target: "HTTP_client_upload", depth: 6, count: 3},
            {source: "photurkey", target: "HTTP_client_blobstore", depth: 6, count: 8},
            {source: "expandodoService", target: "cassie", depth: 7, count: 1},
            {source: "thumbingbird_proxy_thrift", target: "cassie", depth: 7, count: 1},
            {source: "spiderduck", target: "cassie", depth: 7, count: 1},
            {source: "cluster_tweet_button", target: "cassie", depth: 7, count: 3},
            {source: "syndication-thrift", target: "cassie", depth: 7, count: 6},
            {source: "SyndicationService", target: "cassie", depth: 7, count: 39},
            {source: "rainbird.query", target: "cassie", depth: 7, count: 45},
            {source: "cuckoo-client", target: "cassie", depth: 7, count: 114},
            {source: "cuckoo.thrift", target: "cassie", depth: 7, count: 137},
            {source: "com.twitter.service.spiderduck.gen.UrlStoreService", target: "cassie", depth: 7, count: 266},
            {source: "macaw.tweetbutton", target: "cassie", depth: 7, count: 349},
            {source: "image_proxy", target: "memcached", depth: 7, count: 1},
            {source: "cluster_macaw_typeahead", target: "memcached", depth: 7, count: 2},
            {source: "timeline_service", target: "memcached", depth: 7, count: 3},
            {source: "client", target: "memcached", depth: 7, count: 5},
            {source: "expandodoService", target: "memcached", depth: 7, count: 8},
            {source: "cluster_twitterweb_40_unicorn", target: "memcached", depth: 7, count: 9},
            {source: "promptbird", target: "memcached", depth: 7, count: 14},
            {source: "cluster_twitterweb_unicorn", target: "memcached", depth: 7, count: 15},
            {source: "macaw.typeahead", target: "memcached", depth: 7, count: 52},
            {source: "limiter", target: "memcached", depth: 7, count: 62},
            {source: "groups", target: "memcached", depth: 7, count: 81},
            {source: "thumbingbird_proxy_thrift", target: "memcached", depth: 7, count: 105},
            {source: "com.twitter.service.spiderduck.gen.UrlStoreService", target: "memcached", depth: 7, count: 144},
            {source: "cluster_woodstar", target: "memcached", depth: 7, count: 319},
            {source: "macaw.woodstar", target: "memcached", depth: 7, count: 1430},
            {source: "TimelineService", target: "memcached", depth: 7, count: 1437},
            {source: "Limiter", target: "memcached", depth: 7, count: 1497},
            {source: "tweetypie", target: "memcached", depth: 7, count: 10415},
            {source: "monorail", target: "memcached", depth: 7, count: 10441},
            {source: "timeline_service", target: "tflock", depth: 7, count: 12},
            {source: "promptbird", target: "tflock", depth: 7, count: 152},
            {source: "cluster_twitterweb_40_unicorn", target: "tflock", depth: 7, count: 356},
            {source: "cluster_twitterweb_unicorn", target: "tflock", depth: 7, count: 1012},
            {source: "flockdb_edges", target: "tflock", depth: 7, count: 3046},
            {source: "gizmoduck", target: "tflock", depth: 7, count: 58049},
            {source: "TimelineService", target: "tflock", depth: 7, count: 79690},
            {source: "groups", target: "tflock", depth: 7, count: 127905},
            {source: "tweetypie", target: "tflock", depth: 7, count: 286793},
            {source: "monorail", target: "tflock", depth: 7, count: 4904037},
            {source: "cluster_twitterweb_40_unicorn", target: "flock", depth: 8, count: 1},
            {source: "promptbird", target: "flock", depth: 8, count: 1},
            {source: "cluster_twitterweb_unicorn", target: "flock", depth: 8, count: 3},
            {source: "cluster_woodstar", target: "flock", depth: 8, count: 8},
            {source: "TimelineService", target: "flock", depth: 8, count: 147},
            {source: "flock/highQoS", target: "flock", depth: 8, count: 374},
            {source: "macaw.woodstar", target: "flock", depth: 8, count: 495},
            {source: "tweetypie", target: "flock", depth: 8, count: 766},
            {source: "groups", target: "flock", depth: 8, count: 3235},
            {source: "monorail", target: "flock", depth: 8, count: 9878},
            {source: "flockdb_edges", target: "flock", depth: 8, count: 10003},
            {source: "tflock", target: "flock", depth: 8, count: 30612},
            {source: "timeline_service", target: "memcached/magic", depth: 8, count: 2},
            {source: "promptbird", target: "memcached/magic", depth: 8, count: 49},
            {source: "flockdb_edges", target: "memcached/magic", depth: 8, count: 76},
            {source: "cluster_twitterweb_40_unicorn", target: "memcached/magic", depth: 8, count: 115},
            {source: "cluster_twitterweb_unicorn", target: "memcached/magic", depth: 8, count: 417},
            {source: "tflock", target: "memcached/magic", depth: 8, count: 9507},
            {source: "TimelineService", target: "memcached/magic", depth: 8, count: 15857},
            {source: "groups", target: "memcached/magic", depth: 8, count: 22724},
            {source: "gizmoduck", target: "memcached/magic", depth: 8, count: 35055},
            {source: "tweetypie", target: "memcached/magic", depth: 8, count: 83960},
            {source: "monorail", target: "memcached/magic", depth: 8, count: 1011971},
            {source: "cluster_twitterweb_40_unicorn", target: "mysql/email_addresses", depth: 9, count: 1},
            {source: "memcached/magic", target: "mysql/email_addresses", depth: 9, count: 1},
            {source: "memcached/user", target: "mysql/email_addresses", depth: 9, count: 3},
            {source: "cluster_twitterweb_unicorn", target: "mysql/email_addresses", depth: 9, count: 6},
            {source: "TimelineService", target: "mysql/email_addresses", depth: 9, count: 116},
            {source: "groups", target: "mysql/email_addresses", depth: 9, count: 116},
            {source: "tweetypie", target: "mysql/email_addresses", depth: 9, count: 573},
            {source: "monorail", target: "mysql/email_addresses", depth: 9, count: 12103},
            {source: "gizmoduck", target: "mysql/email_addresses", depth: 9, count: 13915}
        ];

        var links = [];
        $.each(linkList, function(k, v) {
            links.push(v);
        });

        var dependencyList = { links: links, root: "planet" };

        var dependencyOptions = {
          width: ($(window).width() > Zipkin.Config.MAX_WINDOW_SIZE ? Zipkin.Config.MAX_GRAPHIC_WIDTH: Zipkin.Config.MIN_GRAPHIC_WIDTH) + 200
        };

        try {
          globalDependencies = new Zipkin.GlobalDependencies(dependencyList, dependencyOptions);
          $('#loading-data').hide();
          $('#global-dependency').html(globalDependencies.chart.el);
          $('#global-dependency').show();
        } catch (e) {
          console.log(e);
          $("#error-msg").text("Something went wrong rendering the dependency tree (╯°□°）╯︵ ┻━┻");
          $(".error-box").show();
          return;
        }

        (function () {
          var prevWidth = $(window).width();
          $(window).resize(function () {
            var newWidth = $(window).width();
            if (Zipkin.Base.windowResized(prevWidth, newWidth)) {
              var w = newWidth > Zipkin.Config.MAX_WINDOW_SIZE ? Zipkin.Config.MAX_GRAPHIC_WIDTH : Zipkin.Config.MIN_GRAPHIC_WIDTH;
              traceDependencies.resize(w + 200);
              prevWidth = newWidth;
            }
           });
        })();
      }
    };
  })();

  return {
    initialize: initialize
  };
})();