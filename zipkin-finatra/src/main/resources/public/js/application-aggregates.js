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

	var nodes = [
	  {name: "groups"},
      {name: "gizmoduck"},
      {name: "promptbird"},
      {name: "cluster_macaw_typeahead"},
      {name: "macaw.typeahead"},
      {name: "TimelineService"},
      {name: "cluster_woodstar"},
      {name: "tweetypie"},
      {name: "monorail"},
      {name: "macaw.woodstar"},
      {name: "peacock"},
      {name: "mysql/twttr_production"},
      {name: "cluster_twitterweb_40_unicorn"},
      {name: "memcached/page"},
      {name: "cluster_twitterweb_unicorn"},
      {name: "mysql/erasures"},
      {name: "twitter.com-ssl"},
      {name: "passbird"},
      {name: "api.twitter.com-plaintext"},
      {name: "api.twitter.com-ssl"},
      {name: "timeline_service"},
      {name: "memcached/user"},
      {name: "mysql/blocks"},
      {name: "urlStoreService"},
      {name: "flockdb_edges"},
      {name: "timelineservice"},
      {name: "haplo/lowQoS"},
      {name: "photurkey"},
      {name: "HTTP_client_blobstore_metadata"},
      {name: "mysql/contributor_permissions"},
      {name: "adserver"},
      {name: "mysql/device_sources"},
      {name: "com.twitter.service.spiderduck.gen.UrlStoreService"},
      {name: "thumbingbird_proxy_thrift"},
      {name: "cluster_tweet_button"},
      {name: "mysql/role_users"},
      {name: "mysql/verified_profile_requests"},
      {name: "mysql/none"},
      {name: "mysql/bouncers"},
      {name: "memcached/object"},
      {name: "cuckoo-client"},
      {name: "client"},
      {name: "gazetteer-buffered"},
      {name: "cluster_rufous"},
      {name: "macaw.rufous"},
      {name: "rainbird.query"},
      {name: "flock"},
      {name: "flock/highQoS"},
      {name: "tflock"},
      {name: "mysql/revss_production"},
      {name: "mysql/saved_searches"},
      {name: "cluster_macaw_swift"},
      {name: "s.twitter.com-ssl"},
      {name: "Ibis"},
      {name: "kestrel"},
      {name: "memcached/trends"},
      {name: "macaw.syndication"},
      {name: "syndication-thrift"},
      {name: "memcached/fragment"},
      {name: "mysql/client_applications"},
      {name: "mysql/oauth_access_tokens_xtra"},
      {name: "cluster_mobile"},
      {name: "mobile.twitter.com-ssl"},
      {name: "memcached/united"},
      {name: "image_proxy"},
      {name: "memcached"},
      {name: "expandodoService"},
      {name: "limiter"},
      {name: "Limiter"},
      {name: "mysql/apple_push_destinations"},
      {name: "mysql/oauth_access_tokens"},
      {name: "mysql/brand_banners"},
      {name: "memcached/lookaside"},
      {name: "mysql/ads_insight"},
      {name: "penguin"},
      {name: "cluster_search_api"},
      {name: "mysql/profile_images"},
      {name: "memcached/magic"},
      {name: "mysql/roles"},
      {name: "cluster_talon"},
      {name: "cassie"},
      {name: "spiderduck"},
      {name: "SyndicationService"},
      {name: "cuckoo.thrift"},
      {name: "macaw.tweetbutton"},
      {name: "mysql/users"},
      {name: "tweetdeck.com-ssl"},
      {name: "cluster_tweetdeck_web_app"},
      {name: "memcached/activity"},
      {name: "mysql/profile_background_images"},
      {name: "mysql/revenue_insight"},
      {name: "tbird"},
      {name: "haplo/highQoS"},
      {name: "tls"},
      {name: "mysql/client_application_images"},
      {name: "mysql/device_languages"},
      {name: "mysql/devices"},
      {name: "talon"},
      {name: "mysql/facebook_connections"},
      {name: "timelineService"},
      {name: "tweetypie.forked"},
      {name: "HTTP_client_upload"},
      {name: "haplo3/lowQoS"},
      {name: "support.twitter.com-ssl"},
      {name: "cluster_support"},
      {name: "metastore"},
      {name: "mysql/invitations"},
      {name: "flock/lowQoS"},
      {name: "mysql/phone_verification_call_logs"},
      {name: "mysql/password_seeds"},
      {name: "mysql/promptbird_campaigns"},
      {name: "r.twimg.com-ssl"},
      {name: "haplo3/highQoS"},
      {name: "mysql/user_tattles"},
      {name: "manhattan"},
      {name: "mysql/promptbird_dismissals"},
      {name: "HTTP_client_blobstore"},
      {name: "image-proxy-origin.twimg.com-ssl"},
      {name: "cluster_image_proxy_origin"},
      {name: "mysql/email_addresses"},
      {name: "memcached/counter"},
      {name: "memcached/rate"},
      {name: "mysql/client_applications_xtra"}
	];

	var links = [
{source: 0, target: 1, value: 0.001},
{source: 2, target: 1, value: 0.001},
{source: 3, target: 1, value: 0.001},
{source: 4, target: 1, value: 0.001},
{source: 5, target: 1, value: 0.001},
{source: 6, target: 1, value: 0.001},
{source: 7, target: 1, value: 0.001},
{source: 8, target: 1, value: 0.001},
{source: 9, target: 1, value: 0.001},
{source: 10, target: 1, value: 0.001},
{source: 10, target: 11, value: 0.00117209242541586},
{source: 12, target: 13, value: 0.001},
{source: 14, target: 13, value: 0.001},
{source: 0, target: 13, value: 0.001},
{source: 5, target: 13, value: 0.001},
{source: 7, target: 13, value: 0.001},
{source: 8, target: 13, value: 0.00103458659570614},
{source: 5, target: 15, value: 0.001},
{source: 8, target: 15, value: 0.001},
{source: 16, target: 17, value: 0.001},
{source: 18, target: 17, value: 0.001},
{source: 19, target: 17, value: 0.001},
{source: 20, target: 21, value: 0.001},
{source: 2, target: 21, value: 0.001},
{source: 12, target: 21, value: 0.001},
{source: 14, target: 21, value: 0.001},
{source: 5, target: 21, value: 0.00122441882496821},
{source: 1, target: 21, value: 0.00167894883019366},
{source: 7, target: 21, value: 0.0021177903778318},
{source: 0, target: 21, value: 0.0028178854320957},
{source: 8, target: 21, value: 0.0387541048309927},
{source: 7, target: 22, value: 0.001},
{source: 8, target: 22, value: 0.001},
{source: 7, target: 23, value: 0.001},
{source: 16, target: 12, value: 0.001},
{source: 18, target: 12, value: 0.001},
{source: 19, target: 12, value: 0.001},
{source: 2, target: 24, value: 0.001},
{source: 20, target: 24, value: 0.001},
{source: 14, target: 24, value: 0.001},
{source: 9, target: 24, value: 0.001},
{source: 25, target: 24, value: 0.001},
{source: 6, target: 24, value: 0.001},
{source: 1, target: 24, value: 0.001},
{source: 0, target: 24, value: 0.001},
{source: 5, target: 24, value: 0.001},
{source: 7, target: 24, value: 0.001},
{source: 8, target: 24, value: 0.001},
{source: 0, target: 26, value: 0.001},
{source: 7, target: 26, value: 0.001},
{source: 5, target: 26, value: 0.001},
{source: 8, target: 26, value: 0.001},
{source: 27, target: 28, value: 0.001},
{source: 0, target: 29, value: 0.001},
{source: 5, target: 29, value: 0.001},
{source: 8, target: 29, value: 0.001},
{source: 6, target: 30, value: 0.001},
{source: 9, target: 30, value: 0.001},
{source: 12, target: 31, value: 0.001},
{source: 14, target: 31, value: 0.001},
{source: 0, target: 31, value: 0.001},
{source: 5, target: 31, value: 0.001},
{source: 7, target: 31, value: 0.001},
{source: 8, target: 31, value: 0.001},
{source: 32, target: 33, value: 0.001},
{source: 18, target: 34, value: 0.001},
{source: 19, target: 9, value: 0.001},
{source: 18, target: 9, value: 0.001},
{source: 12, target: 35, value: 0.001},
{source: 14, target: 35, value: 0.001},
{source: 1, target: 35, value: 0.001},
{source: 5, target: 35, value: 0.001},
{source: 0, target: 35, value: 0.001},
{source: 7, target: 35, value: 0.001},
{source: 8, target: 35, value: 0.00147885949114592},
{source: 7, target: 36, value: 0.001},
{source: 5, target: 36, value: 0.001},
{source: 8, target: 36, value: 0.001},
{source: 14, target: 37, value: 0.001},
{source: 1, target: 37, value: 0.001},
{source: 20, target: 37, value: 0.001},
{source: 0, target: 37, value: 0.001},
{source: 7, target: 37, value: 0.001},
{source: 5, target: 37, value: 0.001},
{source: 8, target: 37, value: 0.001},
{source: 12, target: 38, value: 0.001},
{source: 14, target: 38, value: 0.001},
{source: 5, target: 38, value: 0.001},
{source: 0, target: 38, value: 0.001},
{source: 7, target: 38, value: 0.001},
{source: 1, target: 38, value: 0.001},
{source: 8, target: 38, value: 0.001},
{source: 20, target: 39, value: 0.001},
{source: 2, target: 39, value: 0.001},
{source: 12, target: 39, value: 0.001},
{source: 14, target: 39, value: 0.001},
{source: 5, target: 39, value: 0.001},
{source: 0, target: 39, value: 0.001},
{source: 7, target: 39, value: 0.00152219672621821},
{source: 1, target: 39, value: 0.0123486707277756},
{source: 8, target: 39, value: 0.0331406270947119},
{source: 40, target: 41, value: 0.001},
{source: 42, target: 41, value: 0.001},
{source: 32, target: 41, value: 0.001},
{source: 43, target: 41, value: 0.001},
{source: 44, target: 41, value: 0.001},
{source: 10, target: 45, value: 0.001},
{source: 12, target: 46, value: 0.001},
{source: 2, target: 46, value: 0.001},
{source: 14, target: 46, value: 0.001},
{source: 6, target: 46, value: 0.001},
{source: 5, target: 46, value: 0.001},
{source: 47, target: 46, value: 0.001},
{source: 9, target: 46, value: 0.001},
{source: 7, target: 46, value: 0.001},
{source: 0, target: 46, value: 0.001},
{source: 8, target: 46, value: 0.001},
{source: 24, target: 46, value: 0.001},
{source: 48, target: 46, value: 0.001},
{source: 10, target: 49, value: 0.001},
{source: 14, target: 50, value: 0.001},
{source: 7, target: 50, value: 0.001},
{source: 0, target: 50, value: 0.001},
{source: 5, target: 50, value: 0.001},
{source: 8, target: 50, value: 0.001},
{source: 18, target: 51, value: 0.001},
{source: 52, target: 8, value: 0.001},
{source: 16, target: 8, value: 0.001},
{source: 18, target: 8, value: 0.001},
{source: 19, target: 8, value: 0.001},
{source: 53, target: 54, value: 0.001},
{source: 5, target: 55, value: 0.001},
{source: 14, target: 55, value: 0.001},
{source: 7, target: 55, value: 0.001},
{source: 8, target: 55, value: 0.001},
{source: 56, target: 57, value: 0.001},
{source: 2, target: 58, value: 0.001},
{source: 12, target: 58, value: 0.001},
{source: 14, target: 58, value: 0.001},
{source: 5, target: 58, value: 0.001},
{source: 0, target: 58, value: 0.001},
{source: 7, target: 58, value: 0.00260593039593428},
{source: 8, target: 58, value: 0.041986646227136},
{source: 7, target: 59, value: 0.001},
{source: 14, target: 59, value: 0.001},
{source: 5, target: 59, value: 0.001},
{source: 8, target: 59, value: 0.001},
{source: 20, target: 60, value: 0.001},
{source: 1, target: 60, value: 0.001},
{source: 0, target: 60, value: 0.001},
{source: 5, target: 60, value: 0.001},
{source: 7, target: 60, value: 0.001},
{source: 8, target: 60, value: 0.001},
{source: 6, target: 25, value: 0.001},
{source: 9, target: 25, value: 0.001},
{source: 18, target: 61, value: 0.001},
{source: 62, target: 61, value: 0.001},
{source: 20, target: 63, value: 0.001},
{source: 2, target: 63, value: 0.001},
{source: 12, target: 63, value: 0.001},
{source: 14, target: 63, value: 0.001},
{source: 1, target: 63, value: 0.001},
{source: 0, target: 63, value: 0.0024755401995814},
{source: 5, target: 63, value: 0.00402267949166218},
{source: 7, target: 63, value: 0.00474773603634544},
{source: 8, target: 63, value: 0.056631622677039},
{source: 8, target: 2, value: 0.001},
{source: 64, target: 65, value: 0.001},
{source: 3, target: 65, value: 0.001},
{source: 20, target: 65, value: 0.001},
{source: 41, target: 65, value: 0.001},
{source: 66, target: 65, value: 0.001},
{source: 12, target: 65, value: 0.001},
{source: 2, target: 65, value: 0.001},
{source: 14, target: 65, value: 0.001},
{source: 4, target: 65, value: 0.001},
{source: 67, target: 65, value: 0.001},
{source: 0, target: 65, value: 0.001},
{source: 33, target: 65, value: 0.001},
{source: 32, target: 65, value: 0.001},
{source: 6, target: 65, value: 0.001},
{source: 9, target: 65, value: 0.001},
{source: 5, target: 65, value: 0.001},
{source: 68, target: 65, value: 0.001},
{source: 7, target: 65, value: 0.001},
{source: 8, target: 65, value: 0.001},
{source: 18, target: 68, value: 0.001},
{source: 19, target: 68, value: 0.001},
{source: 14, target: 69, value: 0.001},
{source: 0, target: 69, value: 0.001},
{source: 7, target: 69, value: 0.001},
{source: 5, target: 69, value: 0.001},
{source: 1, target: 69, value: 0.001},
{source: 8, target: 69, value: 0.001},
{source: 20, target: 70, value: 0.001},
{source: 1, target: 70, value: 0.001},
{source: 14, target: 70, value: 0.001},
{source: 0, target: 70, value: 0.001},
{source: 5, target: 70, value: 0.001},
{source: 7, target: 70, value: 0.001},
{source: 8, target: 70, value: 0.001},
{source: 14, target: 71, value: 0.001},
{source: 5, target: 71, value: 0.001},
{source: 0, target: 71, value: 0.001},
{source: 7, target: 71, value: 0.001},
{source: 8, target: 71, value: 0.001},
{source: 20, target: 72, value: 0.001},
{source: 2, target: 72, value: 0.001},
{source: 12, target: 72, value: 0.001},
{source: 14, target: 72, value: 0.001},
{source: 5, target: 72, value: 0.00553435416429952},
{source: 0, target: 72, value: 0.00802910278903305},
{source: 1, target: 72, value: 0.0198344684156338},
{source: 7, target: 72, value: 0.0226347540099054},
{source: 8, target: 72, value: 0.451523687981294},
{source: 18, target: 44, value: 0.001},
{source: 10, target: 73, value: 0.001},
{source: 5, target: 74, value: 0.001},
{source: 8, target: 74, value: 0.001},
{source: 7, target: 74, value: 0.001},
{source: 19, target: 4, value: 0.001},
{source: 16, target: 4, value: 0.001},
{source: 52, target: 75, value: 0.001},
{source: 18, target: 75, value: 0.001},
{source: 0, target: 76, value: 0.001},
{source: 20, target: 76, value: 0.001},
{source: 1, target: 76, value: 0.001},
{source: 7, target: 76, value: 0.001},
{source: 5, target: 76, value: 0.001},
{source: 8, target: 76, value: 0.001},
{source: 20, target: 77, value: 0.001},
{source: 2, target: 77, value: 0.001},
{source: 24, target: 77, value: 0.001},
{source: 12, target: 77, value: 0.001},
{source: 14, target: 77, value: 0.001},
{source: 48, target: 77, value: 0.001},
{source: 5, target: 77, value: 0.001},
{source: 0, target: 77, value: 0.001},
{source: 1, target: 77, value: 0.001},
{source: 7, target: 77, value: 0.00158890578893866},
{source: 8, target: 77, value: 0.0191511026695813},
{source: 5, target: 78, value: 0.001},
{source: 1, target: 78, value: 0.001},
{source: 0, target: 78, value: 0.001},
{source: 8, target: 78, value: 0.001},
{source: 18, target: 79, value: 0.001},
{source: 66, target: 80, value: 0.001},
{source: 33, target: 80, value: 0.001},
{source: 81, target: 80, value: 0.001},
{source: 34, target: 80, value: 0.001},
{source: 57, target: 80, value: 0.001},
{source: 82, target: 80, value: 0.001},
{source: 45, target: 80, value: 0.001},
{source: 40, target: 80, value: 0.001},
{source: 83, target: 80, value: 0.001},
{source: 32, target: 80, value: 0.001},
{source: 84, target: 80, value: 0.001},
{source: 1, target: 85, value: 0.001},
{source: 12, target: 85, value: 0.001},
{source: 14, target: 85, value: 0.001},
{source: 5, target: 85, value: 0.001},
{source: 0, target: 85, value: 0.001},
{source: 7, target: 85, value: 0.001},
{source: 8, target: 85, value: 0.001},
{source: 18, target: 5, value: 0.001},
{source: 19, target: 5, value: 0.001},
{source: 16, target: 5, value: 0.001},
{source: 8, target: 5, value: 0.001},
{source: 9, target: 5, value: 0.001},
{source: 6, target: 5, value: 0.001},
{source: 86, target: 87, value: 0.001},
{source: 12, target: 88, value: 0.001},
{source: 14, target: 88, value: 0.001},
{source: 0, target: 88, value: 0.001},
{source: 5, target: 88, value: 0.001},
{source: 7, target: 88, value: 0.001},
{source: 8, target: 88, value: 0.001},
{source: 20, target: 47, value: 0.001},
{source: 0, target: 47, value: 0.001},
{source: 25, target: 47, value: 0.001},
{source: 7, target: 47, value: 0.001},
{source: 8, target: 47, value: 0.001},
{source: 5, target: 47, value: 0.001},
{source: 20, target: 48, value: 0.001},
{source: 2, target: 48, value: 0.001},
{source: 12, target: 48, value: 0.001},
{source: 14, target: 48, value: 0.001},
{source: 24, target: 48, value: 0.001},
{source: 1, target: 48, value: 0.00109855159769057},
{source: 5, target: 48, value: 0.00150809793140212},
{source: 0, target: 48, value: 0.00242054543752023},
{source: 7, target: 48, value: 0.0054274304183788},
{source: 8, target: 48, value: 0.0928067267564241},
{source: 7, target: 89, value: 0.001},
{source: 5, target: 89, value: 0.001},
{source: 0, target: 89, value: 0.001},
{source: 8, target: 89, value: 0.001},
{source: 10, target: 90, value: 0.001},
{source: 0, target: 91, value: 0.001},
{source: 8, target: 91, value: 0.001},
{source: 7, target: 91, value: 0.001},
{source: 5, target: 91, value: 0.001},
{source: 7, target: 32, value: 0.001},
{source: 14, target: 92, value: 0.001},
{source: 20, target: 92, value: 0.001},
{source: 25, target: 92, value: 0.001},
{source: 93, target: 92, value: 0.001},
{source: 0, target: 92, value: 0.001},
{source: 7, target: 92, value: 0.001},
{source: 5, target: 92, value: 0.001},
{source: 8, target: 92, value: 0.001},
{source: 8, target: 94, value: 0.001},
{source: 1, target: 95, value: 0.001},
{source: 0, target: 95, value: 0.001},
{source: 7, target: 95, value: 0.001},
{source: 5, target: 95, value: 0.001},
{source: 8, target: 95, value: 0.001},
{source: 0, target: 96, value: 0.001},
{source: 5, target: 96, value: 0.001},
{source: 7, target: 96, value: 0.001},
{source: 1, target: 96, value: 0.001},
{source: 8, target: 96, value: 0.001},
{source: 5, target: 97, value: 0.001},
{source: 8, target: 97, value: 0.001},
{source: 7, target: 97, value: 0.001},
{source: 12, target: 98, value: 0.001},
{source: 2, target: 98, value: 0.001},
{source: 14, target: 98, value: 0.001},
{source: 7, target: 98, value: 0.001},
{source: 0, target: 98, value: 0.001},
{source: 5, target: 98, value: 0.001},
{source: 8, target: 98, value: 0.001},
{source: 18, target: 6, value: 0.001},
{source: 19, target: 6, value: 0.001},
{source: 7, target: 99, value: 0.001},
{source: 16, target: 7, value: 0.001},
{source: 14, target: 7, value: 0.001},
{source: 18, target: 7, value: 0.001},
{source: 25, target: 7, value: 0.001},
{source: 100, target: 7, value: 0.001},
{source: 19, target: 7, value: 0.001},
{source: 75, target: 7, value: 0.001},
{source: 5, target: 7, value: 0.001},
{source: 6, target: 7, value: 0.001},
{source: 8, target: 7, value: 0.001},
{source: 10, target: 7, value: 0.001},
{source: 9, target: 7, value: 0.001},
{source: 27, target: 101, value: 0.001},
{source: 56, target: 82, value: 0.001},
{source: 7, target: 102, value: 0.001},
{source: 5, target: 102, value: 0.001},
{source: 8, target: 102, value: 0.001},
{source: 103, target: 104, value: 0.001},
{source: 5, target: 105, value: 0.001},
{source: 8, target: 105, value: 0.001},
{source: 1, target: 106, value: 0.001},
{source: 8, target: 106, value: 0.001},
{source: 52, target: 14, value: 0.001},
{source: 16, target: 14, value: 0.001},
{source: 18, target: 14, value: 0.001},
{source: 19, target: 14, value: 0.001},
{source: 8, target: 107, value: 0.001},
{source: 5, target: 108, value: 0.001},
{source: 14, target: 109, value: 0.001},
{source: 5, target: 109, value: 0.001},
{source: 8, target: 109, value: 0.001},
{source: 5, target: 0, value: 0.001},
{source: 18, target: 0, value: 0.001},
{source: 19, target: 0, value: 0.001},
{source: 8, target: 0, value: 0.001},
{source: 7, target: 66, value: 0.001},
{source: 0, target: 27, value: 0.001},
{source: 7, target: 27, value: 0.001},
{source: 5, target: 27, value: 0.001},
{source: 8, target: 27, value: 0.001},
{source: 0, target: 110, value: 0.001},
{source: 8, target: 110, value: 0.001},
{source: 111, target: 43, value: 0.001},
{source: 18, target: 43, value: 0.001},
{source: 14, target: 20, value: 0.001},
{source: 8, target: 20, value: 0.001},
{source: 10, target: 93, value: 0.001},
{source: 99, target: 112, value: 0.001},
{source: 14, target: 112, value: 0.001},
{source: 12, target: 112, value: 0.001},
{source: 20, target: 112, value: 0.001},
{source: 25, target: 112, value: 0.001},
{source: 0, target: 112, value: 0.001},
{source: 7, target: 112, value: 0.001},
{source: 5, target: 112, value: 0.001},
{source: 8, target: 112, value: 0.001},
{source: 7, target: 113, value: 0.001},
{source: 8, target: 113, value: 0.001},
{source: 66, target: 114, value: 0.001},
{source: 32, target: 114, value: 0.001},
{source: 14, target: 115, value: 0.001},
{source: 5, target: 115, value: 0.001},
{source: 0, target: 115, value: 0.001},
{source: 8, target: 115, value: 0.001},
{source: 27, target: 116, value: 0.001},
{source: 117, target: 118, value: 0.001},
{source: 12, target: 119, value: 0.001},
{source: 77, target: 119, value: 0.001},
{source: 21, target: 119, value: 0.001},
{source: 14, target: 119, value: 0.001},
{source: 5, target: 119, value: 0.001},
{source: 0, target: 119, value: 0.001},
{source: 7, target: 119, value: 0.001},
{source: 8, target: 119, value: 0.001},
{source: 1, target: 119, value: 0.001},
{source: 20, target: 120, value: 0.001},
{source: 2, target: 120, value: 0.001},
{source: 12, target: 120, value: 0.001},
{source: 14, target: 120, value: 0.001},
{source: 1, target: 120, value: 0.00154161332149514},
{source: 5, target: 120, value: 0.00244280071631718},
{source: 7, target: 120, value: 0.00709544085474199},
{source: 0, target: 120, value: 0.0096166460098821},
{source: 8, target: 120, value: 0.110967791237007},
{source: 18, target: 3, value: 0.001},
{source: 16, target: 3, value: 0.001},
{source: 19, target: 3, value: 0.001},
{source: 19, target: 67, value: 0.001},
{source: 18, target: 67, value: 0.001},
{source: 12, target: 121, value: 0.001},
{source: 14, target: 121, value: 0.001},
{source: 0, target: 121, value: 0.001},
{source: 5, target: 121, value: 0.001},
{source: 7, target: 121, value: 0.001},
{source: 1, target: 121, value: 0.001},
{source: 8, target: 121, value: 0.0132477007231225},
{source: 32, target: 64, value: 0.001},
{source: 7, target: 122, value: 0.001},
{source: 0, target: 122, value: 0.001},
{source: 5, target: 122, value: 0.001},
{source: 8, target: 122, value: 0.001}
	];


/*        var links = [];
        $.each(linkList, function(k, v) {
            links.push(v);
        });*/

        //var dependencyList = { links: links, root: "planet" };

        var dependencyOptions = {
          width: ($(window).width() > Zipkin.Config.MAX_AGG_WINDOW_SIZE ? Zipkin.Config.MAX_AGG_GRAPHIC_WIDTH: Zipkin.Config.MIN_AGG_GRAPHIC_WIDTH) + 200
        };

        try {
          $('#loading-data').hide();
          var globalDependencies = new Zipkin.GlobalDependencies(nodes, links, dependencyOptions);
          globalDependencies.chart;
//          $('#global-dependency').html(globalDependencies.chart());
          $('#global-dependency').show();
        } catch (e) {
          console.log(e);
          $("#error-msg").text("Something went wrong rendering the dependency tree :(");
          $(".error-box").show();
          return;
        }

        (function () {
          var prevWidth = $(window).width();
          $(window).resize(function () {
            var newWidth = $(window).width();
            if (Zipkin.Base.windowResized(prevWidth, newWidth)) {
              var w = newWidth >= Zipkin.Config.MAX_AGG_WINDOW_SIZE ? Zipkin.Config.MAX_AGG_GRAPHIC_WIDTH : Zipkin.Config.MIN_AGG_GRAPHIC_WIDTH;
              globalDependencies.resize(w + 200);
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