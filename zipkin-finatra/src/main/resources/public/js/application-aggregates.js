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

    var href = window.location.href;

    $(".js-zipkin-navbar > li > a").each(function (index, elem) {
      var parent = $(elem).parent();
      if ($(elem).attr("href") == href ) {
        parent.addClass("active");
      } else if (parent.hasClass("active")) {
        parent.removeClass("active");
      }
    });

    var pillSelector = function(name) {
      return $("[id=" + name + "-pill]").parent();
    }

    pillSelector("dependencies").on('click', function(event) {
      pillSelector("service-report").removeClass("active");
      pillSelector("dependencies").addClass("active");
      $(".container").width(1500);
      $("#service-report").hide();
      $("#global-dependency").show();
    });

    pillSelector("service-report").on('click', function(event) {
      pillSelector("dependencies").removeClass("active");
      pillSelector("service-report").addClass("active");
      $(".container").width(1210);
      $("#global-dependency").hide();
      $("#service-report").show();
    });


    $('.date-input').each(function() {
      var self = $(this)
        , self_val = self.val();

      $(this).DatePicker({
        eventName: 'focus',
        format:'m-d-Y',
        date: self_val,
        current: self_val,
        starts: 0,
        // calendars: 2,
        // mode: "range",
        onBeforeShow: function(){
          self.DatePickerSetDate(self_val, true);
        },
        onChange: function(formated, dates){
          self.val(formated);
          // self.DatePickerHide();
        }
      }).blur(function(){
        // $(this).DatePickerHide();
      });
    });

    $(".nav-dropdowns > li > ul > li").each(function(index, elem) {
      $(elem).on('mouseover', function (event) {
        $(".nav-dropdowns > li > ul > li").each(function (i, e) {
          $(e).removeClass("active");
        });
      }).on('click', function(event) {
        $(elem).addClass("active");
        s = $("#global-dependency > div");
        $("#global-dependency").empty();
        $("#global-dependency").append(s);
        getDependencyTree();
      });
    })

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

    var nodes = [{name: "groups"},
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
                       {name: "mysql/client_applications_xtra"}];

	var links = [{source: 0, target: 1, value: 0.001, count: 2},
                       {source: 2, target: 1, value: 0.001, count: 6},
                       {source: 3, target: 1, value: 0.001, count: 7},
                       {source: 4, target: 1, value: 0.001, count: 49},
                       {source: 5, target: 1, value: 0.001, count: 95},
                       {source: 6, target: 1, value: 0.001, count: 197},
                       {source: 7, target: 1, value: 0.001, count: 254},
                       {source: 8, target: 1, value: 0.001, count: 465},
                       {source: 9, target: 1, value: 0.001, count: 2239},
                       {source: 10, target: 1, value: 0.001, count: 17566},
                       {source: 10, target: 11, value: 0.00117209242541586, count: 61935},
                       {source: 12, target: 13, value: 0.001, count: 23},
                       {source: 14, target: 13, value: 0.001, count: 86},
                       {source: 0, target: 13, value: 0.001, count: 419},
                       {source: 5, target: 13, value: 0.001, count: 683},
                       {source: 7, target: 13, value: 0.001, count: 2609},
                       {source: 8, target: 13, value: 0.00103458659570614, count: 54669},
                       {source: 5, target: 15, value: 0.001, count: 4},
                       {source: 8, target: 15, value: 0.001, count: 20},
                       {source: 16, target: 17, value: 0.001, count: 37},
                       {source: 18, target: 17, value: 0.001, count: 618},
                       {source: 19, target: 17, value: 0.001, count: 1178},
                       {source: 20, target: 21, value: 0.001, count: 3},
                       {source: 2, target: 21, value: 0.001, count: 36},
                       {source: 12, target: 21, value: 0.001, count: 413},
                       {source: 14, target: 21, value: 0.001, count: 1548},
                       {source: 5, target: 21, value: 0.00122441882496821, count: 64700},
                       {source: 1, target: 21, value: 0.00167894883019366, count: 88718},
                       {source: 7, target: 21, value: 0.0021177903778318, count: 111907},
                       {source: 0, target: 21, value: 0.0028178854320957, count: 148901},
                       {source: 8, target: 21, value: 0.0387541048309927, count: 2047821},
                       {source: 7, target: 22, value: 0.001, count: 2},
                       {source: 8, target: 22, value: 0.001, count: 35},
                       {source: 7, target: 23, value: 0.001, count: 1},
                       {source: 16, target: 12, value: 0.001, count: 18},
                       {source: 18, target: 12, value: 0.001, count: 26},
                       {source: 19, target: 12, value: 0.001, count: 28},
                       {source: 2, target: 24, value: 0.001, count: 1},
                       {source: 20, target: 24, value: 0.001, count: 1},
                       {source: 14, target: 24, value: 0.001, count: 4},
                       {source: 9, target: 24, value: 0.001, count: 8},
                       {source: 25, target: 24, value: 0.001, count: 14},
                       {source: 6, target: 24, value: 0.001, count: 15},
                       {source: 1, target: 24, value: 0.001, count: 254},
                       {source: 0, target: 24, value: 0.001, count: 485},
                       {source: 5, target: 24, value: 0.001, count: 500},
                       {source: 7, target: 24, value: 0.001, count: 1390},
                       {source: 8, target: 24, value: 0.001, count: 18138},
                       {source: 0, target: 26, value: 0.001, count: 1},
                       {source: 7, target: 26, value: 0.001, count: 6},
                       {source: 5, target: 26, value: 0.001, count: 13},
                       {source: 8, target: 26, value: 0.001, count: 43},
                       {source: 27, target: 28, value: 0.001, count: 12},
                       {source: 0, target: 29, value: 0.001, count: 5},
                       {source: 5, target: 29, value: 0.001, count: 6},
                       {source: 8, target: 29, value: 0.001, count: 447},
                       {source: 6, target: 30, value: 0.001, count: 4},
                       {source: 9, target: 30, value: 0.001, count: 120},
                       {source: 12, target: 31, value: 0.001, count: 2},
                       {source: 14, target: 31, value: 0.001, count: 16},
                       {source: 0, target: 31, value: 0.001, count: 117},
                       {source: 5, target: 31, value: 0.001, count: 255},
                       {source: 7, target: 31, value: 0.001, count: 2565},
                       {source: 8, target: 31, value: 0.001, count: 32207},
                       {source: 32, target: 33, value: 0.001, count: 1},
                       {source: 18, target: 34, value: 0.001, count: 4},
                       {source: 19, target: 9, value: 0.001, count: 3},
                       {source: 18, target: 9, value: 0.001, count: 4},
                       {source: 12, target: 35, value: 0.001, count: 7},
                       {source: 14, target: 35, value: 0.001, count: 31},
                       {source: 1, target: 35, value: 0.001, count: 908},
                       {source: 5, target: 35, value: 0.001, count: 1330},
                       {source: 0, target: 35, value: 0.001, count: 1852},
                       {source: 7, target: 35, value: 0.001, count: 4056},
                       {source: 8, target: 35, value: 0.00147885949114592, count: 78145},
                       {source: 7, target: 36, value: 0.001, count: 7},
                       {source: 5, target: 36, value: 0.001, count: 8},
                       {source: 8, target: 36, value: 0.001, count: 45},
                       {source: 14, target: 37, value: 0.001, count: 3},
                       {source: 1, target: 37, value: 0.001, count: 7},
                       {source: 20, target: 37, value: 0.001, count: 15},
                       {source: 0, target: 37, value: 0.001, count: 22},
                       {source: 7, target: 37, value: 0.001, count: 827},
                       {source: 5, target: 37, value: 0.001, count: 1684},
                       {source: 8, target: 37, value: 0.001, count: 10128},
                       {source: 12, target: 38, value: 0.001, count: 1},
                       {source: 14, target: 38, value: 0.001, count: 3},
                       {source: 5, target: 38, value: 0.001, count: 106},
                       {source: 0, target: 38, value: 0.001, count: 115},
                       {source: 7, target: 38, value: 0.001, count: 192},
                       {source: 1, target: 38, value: 0.001, count: 2722},
                       {source: 8, target: 38, value: 0.001, count: 6677},
                       {source: 20, target: 39, value: 0.001, count: 6},
                       {source: 2, target: 39, value: 0.001, count: 50},
                       {source: 12, target: 39, value: 0.001, count: 301},
                       {source: 14, target: 39, value: 0.001, count: 1191},
                       {source: 5, target: 39, value: 0.001, count: 7205},
                       {source: 0, target: 39, value: 0.001, count: 8036},
                       {source: 7, target: 39, value: 0.00152219672621821, count: 80435},
                       {source: 1, target: 39, value: 0.0123486707277756, count: 652521},
                       {source: 8, target: 39, value: 0.0331406270947119, count: 1751197},
                       {source: 40, target: 41, value: 0.001, count: 1},
                       {source: 42, target: 41, value: 0.001, count: 12},
                       {source: 32, target: 41, value: 0.001, count: 18},
                       {source: 43, target: 41, value: 0.001, count: 396},
                       {source: 44, target: 41, value: 0.001, count: 1116},
                       {source: 10, target: 45, value: 0.001, count: 3033},
                       {source: 12, target: 46, value: 0.001, count: 1},
                       {source: 2, target: 46, value: 0.001, count: 1},
                       {source: 14, target: 46, value: 0.001, count: 3},
                       {source: 6, target: 46, value: 0.001, count: 8},
                       {source: 5, target: 46, value: 0.001, count: 147},
                       {source: 47, target: 46, value: 0.001, count: 374},
                       {source: 9, target: 46, value: 0.001, count: 495},
                       {source: 7, target: 46, value: 0.001, count: 766},
                       {source: 0, target: 46, value: 0.001, count: 3235},
                       {source: 8, target: 46, value: 0.001, count: 9878},
                       {source: 24, target: 46, value: 0.001, count: 10003},
                       {source: 48, target: 46, value: 0.001, count: 30612},
                       {source: 10, target: 49, value: 0.001, count: 19815},
                       {source: 14, target: 50, value: 0.001, count: 1},
                       {source: 7, target: 50, value: 0.001, count: 5},
                       {source: 0, target: 50, value: 0.001, count: 6},
                       {source: 5, target: 50, value: 0.001, count: 11},
                       {source: 8, target: 50, value: 0.001, count: 1142},
                       {source: 18, target: 51, value: 0.001, count: 3},
                       {source: 52, target: 8, value: 0.001, count: 30},
                       {source: 16, target: 8, value: 0.001, count: 1129},
                       {source: 18, target: 8, value: 0.001, count: 1217},
                       {source: 19, target: 8, value: 0.001, count: 3089},
                       {source: 53, target: 54, value: 0.001, count: 27},
                       {source: 5, target: 55, value: 0.001, count: 2},
                       {source: 14, target: 55, value: 0.001, count: 4},
                       {source: 7, target: 55, value: 0.001, count: 113},
                       {source: 8, target: 55, value: 0.001, count: 22014},
                       {source: 56, target: 57, value: 0.001, count: 8},
                       {source: 2, target: 58, value: 0.001, count: 54},
                       {source: 12, target: 58, value: 0.001, count: 124},
                       {source: 14, target: 58, value: 0.001, count: 365},
                       {source: 5, target: 58, value: 0.001, count: 22723},
                       {source: 0, target: 58, value: 0.001, count: 40864},
                       {source: 7, target: 58, value: 0.00260593039593428, count: 137701},
                       {source: 8, target: 58, value: 0.041986646227136, count: 2218633},
                       {source: 7, target: 59, value: 0.001, count: 2},
                       {source: 14, target: 59, value: 0.001, count: 3},
                       {source: 5, target: 59, value: 0.001, count: 5},
                       {source: 8, target: 59, value: 0.001, count: 424},
                       {source: 20, target: 60, value: 0.001, count: 1},
                       {source: 1, target: 60, value: 0.001, count: 3},
                       {source: 0, target: 60, value: 0.001, count: 9},
                       {source: 5, target: 60, value: 0.001, count: 53},
                       {source: 7, target: 60, value: 0.001, count: 161},
                       {source: 8, target: 60, value: 0.001, count: 1372},
                       {source: 6, target: 25, value: 0.001, count: 55},
                       {source: 9, target: 25, value: 0.001, count: 1229},
                       {source: 18, target: 61, value: 0.001, count: 15},
                       {source: 62, target: 61, value: 0.001, count: 130},
                       {source: 20, target: 63, value: 0.001, count: 6},
                       {source: 2, target: 63, value: 0.001, count: 94},
                       {source: 12, target: 63, value: 0.001, count: 162},
                       {source: 14, target: 63, value: 0.001, count: 572},
                       {source: 1, target: 63, value: 0.001, count: 20314},
                       {source: 0, target: 63, value: 0.0024755401995814, count: 130811},
                       {source: 5, target: 63, value: 0.00402267949166218, count: 212564},
                       {source: 7, target: 63, value: 0.00474773603634544, count: 250877},
                       {source: 8, target: 63, value: 0.056631622677039, count: 2992494},
                       {source: 8, target: 2, value: 0.001, count: 16},
                       {source: 64, target: 65, value: 0.001, count: 1},
                       {source: 3, target: 65, value: 0.001, count: 2},
                       {source: 20, target: 65, value: 0.001, count: 3},
                       {source: 41, target: 65, value: 0.001, count: 5},
                       {source: 66, target: 65, value: 0.001, count: 8},
                       {source: 12, target: 65, value: 0.001, count: 9},
                       {source: 2, target: 65, value: 0.001, count: 14},
                       {source: 14, target: 65, value: 0.001, count: 15},
                       {source: 4, target: 65, value: 0.001, count: 52},
                       {source: 67, target: 65, value: 0.001, count: 62},
                       {source: 0, target: 65, value: 0.001, count: 81},
                       {source: 33, target: 65, value: 0.001, count: 105},
                       {source: 32, target: 65, value: 0.001, count: 144},
                       {source: 6, target: 65, value: 0.001, count: 319},
                       {source: 9, target: 65, value: 0.001, count: 1430},
                       {source: 5, target: 65, value: 0.001, count: 1437},
                       {source: 68, target: 65, value: 0.001, count: 1497},
                       {source: 7, target: 65, value: 0.001, count: 10415},
                       {source: 8, target: 65, value: 0.001, count: 10441},
                       {source: 18, target: 68, value: 0.001, count: 4},
                       {source: 19, target: 68, value: 0.001, count: 5},
                       {source: 14, target: 69, value: 0.001, count: 5},
                       {source: 0, target: 69, value: 0.001, count: 19},
                       {source: 7, target: 69, value: 0.001, count: 70},
                       {source: 5, target: 69, value: 0.001, count: 106},
                       {source: 1, target: 69, value: 0.001, count: 546},
                       {source: 8, target: 69, value: 0.001, count: 4363},
                       {source: 20, target: 70, value: 0.001, count: 1},
                       {source: 1, target: 70, value: 0.001, count: 3},
                       {source: 14, target: 70, value: 0.001, count: 3},
                       {source: 0, target: 70, value: 0.001, count: 10},
                       {source: 5, target: 70, value: 0.001, count: 87},
                       {source: 7, target: 70, value: 0.001, count: 384},
                       {source: 8, target: 70, value: 0.001, count: 3522},
                       {source: 14, target: 71, value: 0.001, count: 2},
                       {source: 5, target: 71, value: 0.001, count: 63},
                       {source: 0, target: 71, value: 0.001, count: 391},
                       {source: 7, target: 71, value: 0.001, count: 561},
                       {source: 8, target: 71, value: 0.001, count: 5352},
                       {source: 20, target: 72, value: 0.001, count: 25},
                       {source: 2, target: 72, value: 0.001, count: 460},
                       {source: 12, target: 72, value: 0.001, count: 2073},
                       {source: 14, target: 72, value: 0.001, count: 7114},
                       {source: 5, target: 72, value: 0.00553435416429952, count: 292443},
                       {source: 0, target: 72, value: 0.00802910278903305, count: 424269},
                       {source: 1, target: 72, value: 0.0198344684156338, count: 1048081},
                       {source: 7, target: 72, value: 0.0226347540099054, count: 1196052},
                       {source: 8, target: 72, value: 0.451523687981294, count: 23859142},
                       {source: 18, target: 44, value: 0.001, count: 6},
                       {source: 10, target: 73, value: 0.001, count: 329},
                       {source: 5, target: 74, value: 0.001, count: 3},
                       {source: 8, target: 74, value: 0.001, count: 9},
                       {source: 7, target: 74, value: 0.001, count: 62},
                       {source: 19, target: 4, value: 0.001, count: 1},
                       {source: 16, target: 4, value: 0.001, count: 2},
                       {source: 52, target: 75, value: 0.001, count: 20},
                       {source: 18, target: 75, value: 0.001, count: 464},
                       {source: 0, target: 76, value: 0.001, count: 2},
                       {source: 20, target: 76, value: 0.001, count: 2},
                       {source: 1, target: 76, value: 0.001, count: 41},
                       {source: 7, target: 76, value: 0.001, count: 46},
                       {source: 5, target: 76, value: 0.001, count: 164},
                       {source: 8, target: 76, value: 0.001, count: 2332},
                       {source: 20, target: 77, value: 0.001, count: 2},
                       {source: 2, target: 77, value: 0.001, count: 49},
                       {source: 24, target: 77, value: 0.001, count: 76},
                       {source: 12, target: 77, value: 0.001, count: 115},
                       {source: 14, target: 77, value: 0.001, count: 417},
                       {source: 48, target: 77, value: 0.001, count: 9507},
                       {source: 5, target: 77, value: 0.001, count: 15857},
                       {source: 0, target: 77, value: 0.001, count: 22724},
                       {source: 1, target: 77, value: 0.001, count: 35055},
                       {source: 7, target: 77, value: 0.00158890578893866, count: 83960},
                       {source: 8, target: 77, value: 0.0191511026695813, count: 1011971},
                       {source: 5, target: 78, value: 0.001, count: 2},
                       {source: 1, target: 78, value: 0.001, count: 3},
                       {source: 0, target: 78, value: 0.001, count: 4},
                       {source: 8, target: 78, value: 0.001, count: 9},
                       {source: 18, target: 79, value: 0.001, count: 30},
                       {source: 66, target: 80, value: 0.001, count: 1},
                       {source: 33, target: 80, value: 0.001, count: 1},
                       {source: 81, target: 80, value: 0.001, count: 1},
                       {source: 34, target: 80, value: 0.001, count: 3},
                       {source: 57, target: 80, value: 0.001, count: 6},
                       {source: 82, target: 80, value: 0.001, count: 39},
                       {source: 45, target: 80, value: 0.001, count: 45},
                       {source: 40, target: 80, value: 0.001, count: 114},
                       {source: 83, target: 80, value: 0.001, count: 137},
                       {source: 32, target: 80, value: 0.001, count: 266},
                       {source: 84, target: 80, value: 0.001, count: 349},
                       {source: 1, target: 85, value: 0.001, count: 3},
                       {source: 12, target: 85, value: 0.001, count: 7},
                       {source: 14, target: 85, value: 0.001, count: 12},
                       {source: 5, target: 85, value: 0.001, count: 166},
                       {source: 0, target: 85, value: 0.001, count: 171},
                       {source: 7, target: 85, value: 0.001, count: 1273},
                       {source: 8, target: 85, value: 0.001, count: 24971},
                       {source: 18, target: 5, value: 0.001, count: 2},
                       {source: 19, target: 5, value: 0.001, count: 4},
                       {source: 16, target: 5, value: 0.001, count: 6},
                       {source: 8, target: 5, value: 0.001, count: 9},
                       {source: 9, target: 5, value: 0.001, count: 11},
                       {source: 6, target: 5, value: 0.001, count: 54},
                       {source: 86, target: 87, value: 0.001, count: 43},
                       {source: 12, target: 88, value: 0.001, count: 7},
                       {source: 14, target: 88, value: 0.001, count: 12},
                       {source: 0, target: 88, value: 0.001, count: 221},
                       {source: 5, target: 88, value: 0.001, count: 485},
                       {source: 7, target: 88, value: 0.001, count: 2353},
                       {source: 8, target: 88, value: 0.001, count: 46505},
                       {source: 20, target: 47, value: 0.001, count: 2},
                       {source: 0, target: 47, value: 0.001, count: 5},
                       {source: 25, target: 47, value: 0.001, count: 55},
                       {source: 7, target: 47, value: 0.001, count: 164},
                       {source: 8, target: 47, value: 0.001, count: 694},
                       {source: 5, target: 47, value: 0.001, count: 1161},
                       {source: 20, target: 48, value: 0.001, count: 12},
                       {source: 2, target: 48, value: 0.001, count: 152},
                       {source: 12, target: 48, value: 0.001, count: 356},
                       {source: 14, target: 48, value: 0.001, count: 1012},
                       {source: 24, target: 48, value: 0.001, count: 3046},
                       {source: 1, target: 48, value: 0.00109855159769057, count: 58049},
                       {source: 5, target: 48, value: 0.00150809793140212, count: 79690},
                       {source: 0, target: 48, value: 0.00242054543752023, count: 127905},
                       {source: 7, target: 48, value: 0.0054274304183788, count: 286793},
                       {source: 8, target: 48, value: 0.0928067267564241, count: 4904037},
                       {source: 7, target: 89, value: 0.001, count: 157},
                       {source: 5, target: 89, value: 0.001, count: 185},
                       {source: 0, target: 89, value: 0.001, count: 218},
                       {source: 8, target: 89, value: 0.001, count: 3422},
                       {source: 10, target: 90, value: 0.001, count: 422},
                       {source: 0, target: 91, value: 0.001, count: 2},
                       {source: 8, target: 91, value: 0.001, count: 46},
                       {source: 7, target: 91, value: 0.001, count: 47},
                       {source: 5, target: 91, value: 0.001, count: 52},
                       {source: 7, target: 32, value: 0.001, count: 1},
                       {source: 14, target: 92, value: 0.001, count: 4},
                       {source: 20, target: 92, value: 0.001, count: 18},
                       {source: 25, target: 92, value: 0.001, count: 34},
                       {source: 93, target: 92, value: 0.001, count: 39},
                       {source: 0, target: 92, value: 0.001, count: 80},
                       {source: 7, target: 92, value: 0.001, count: 395},
                       {source: 5, target: 92, value: 0.001, count: 2557},
                       {source: 8, target: 92, value: 0.001, count: 6117},
                       {source: 8, target: 94, value: 0.001, count: 10},
                       {source: 1, target: 95, value: 0.001, count: 2},
                       {source: 0, target: 95, value: 0.001, count: 3},
                       {source: 7, target: 95, value: 0.001, count: 56},
                       {source: 5, target: 95, value: 0.001, count: 154},
                       {source: 8, target: 95, value: 0.001, count: 399},
                       {source: 0, target: 96, value: 0.001, count: 12},
                       {source: 5, target: 96, value: 0.001, count: 41},
                       {source: 7, target: 96, value: 0.001, count: 138},
                       {source: 1, target: 96, value: 0.001, count: 478},
                       {source: 8, target: 96, value: 0.001, count: 2825},
                       {source: 5, target: 97, value: 0.001, count: 1},
                       {source: 8, target: 97, value: 0.001, count: 13},
                       {source: 7, target: 97, value: 0.001, count: 15},
                       {source: 12, target: 98, value: 0.001, count: 1},
                       {source: 2, target: 98, value: 0.001, count: 6},
                       {source: 14, target: 98, value: 0.001, count: 6},
                       {source: 7, target: 98, value: 0.001, count: 13},
                       {source: 0, target: 98, value: 0.001, count: 212},
                       {source: 5, target: 98, value: 0.001, count: 291},
                       {source: 8, target: 98, value: 0.001, count: 18535},
                       {source: 18, target: 6, value: 0.001, count: 923},
                       {source: 19, target: 6, value: 0.001, count: 1093},
                       {source: 7, target: 99, value: 0.001, count: 21},
                       {source: 16, target: 7, value: 0.001, count: 1},
                       {source: 14, target: 7, value: 0.001, count: 1},
                       {source: 18, target: 7, value: 0.001, count: 1},
                       {source: 25, target: 7, value: 0.001, count: 1},
                       {source: 100, target: 7, value: 0.001, count: 3},
                       {source: 19, target: 7, value: 0.001, count: 5},
                       {source: 75, target: 7, value: 0.001, count: 9},
                       {source: 5, target: 7, value: 0.001, count: 32},
                       {source: 6, target: 7, value: 0.001, count: 69},
                       {source: 8, target: 7, value: 0.001, count: 354},
                       {source: 10, target: 7, value: 0.001, count: 936},
                       {source: 9, target: 7, value: 0.001, count: 1014},
                       {source: 27, target: 101, value: 0.001, count: 3},
                       {source: 56, target: 82, value: 0.001, count: 33},
                       {source: 7, target: 102, value: 0.001, count: 4},
                       {source: 5, target: 102, value: 0.001, count: 15},
                       {source: 8, target: 102, value: 0.001, count: 94},
                       {source: 103, target: 104, value: 0.001, count: 3},
                       {source: 5, target: 105, value: 0.001, count: 18},
                       {source: 8, target: 105, value: 0.001, count: 22},
                       {source: 1, target: 106, value: 0.001, count: 16},
                       {source: 8, target: 106, value: 0.001, count: 17},
                       {source: 52, target: 14, value: 0.001, count: 2},
                       {source: 16, target: 14, value: 0.001, count: 76},
                       {source: 18, target: 14, value: 0.001, count: 85},
                       {source: 19, target: 14, value: 0.001, count: 90},
                       {source: 8, target: 107, value: 0.001, count: 4},
                       {source: 5, target: 108, value: 0.001, count: 4},
                       {source: 14, target: 109, value: 0.001, count: 2},
                       {source: 5, target: 109, value: 0.001, count: 2},
                       {source: 8, target: 109, value: 0.001, count: 243},
                       {source: 5, target: 0, value: 0.001, count: 1},
                       {source: 18, target: 0, value: 0.001, count: 1},
                       {source: 19, target: 0, value: 0.001, count: 6},
                       {source: 8, target: 0, value: 0.001, count: 2604},
                       {source: 7, target: 66, value: 0.001, count: 32},
                       {source: 0, target: 27, value: 0.001, count: 1},
                       {source: 7, target: 27, value: 0.001, count: 26},
                       {source: 5, target: 27, value: 0.001, count: 34},
                       {source: 8, target: 27, value: 0.001, count: 172},
                       {source: 0, target: 110, value: 0.001, count: 1},
                       {source: 8, target: 110, value: 0.001, count: 5},
                       {source: 111, target: 43, value: 0.001, count: 50},
                       {source: 18, target: 43, value: 0.001, count: 1267},
                       {source: 14, target: 20, value: 0.001, count: 2},
                       {source: 8, target: 20, value: 0.001, count: 2690},
                       {source: 10, target: 93, value: 0.001, count: 205},
                       {source: 99, target: 112, value: 0.001, count: 1},
                       {source: 14, target: 112, value: 0.001, count: 2},
                       {source: 12, target: 112, value: 0.001, count: 3},
                       {source: 20, target: 112, value: 0.001, count: 12},
                       {source: 25, target: 112, value: 0.001, count: 19},
                       {source: 0, target: 112, value: 0.001, count: 40},
                       {source: 7, target: 112, value: 0.001, count: 478},
                       {source: 5, target: 112, value: 0.001, count: 3643},
                       {source: 8, target: 112, value: 0.001, count: 5240},
                       {source: 7, target: 113, value: 0.001, count: 8},
                       {source: 8, target: 113, value: 0.001, count: 70},
                       {source: 66, target: 114, value: 0.001, count: 1},
                       {source: 32, target: 114, value: 0.001, count: 61},
                       {source: 14, target: 115, value: 0.001, count: 1},
                       {source: 5, target: 115, value: 0.001, count: 2},
                       {source: 0, target: 115, value: 0.001, count: 2},
                       {source: 8, target: 115, value: 0.001, count: 282},
                       {source: 27, target: 116, value: 0.001, count: 8},
                       {source: 117, target: 118, value: 0.001, count: 7},
                       {source: 12, target: 119, value: 0.001, count: 1},
                       {source: 77, target: 119, value: 0.001, count: 1},
                       {source: 21, target: 119, value: 0.001, count: 3},
                       {source: 14, target: 119, value: 0.001, count: 6},
                       {source: 5, target: 119, value: 0.001, count: 116},
                       {source: 0, target: 119, value: 0.001, count: 116},
                       {source: 7, target: 119, value: 0.001, count: 573},
                       {source: 8, target: 119, value: 0.001, count: 12103},
                       {source: 1, target: 119, value: 0.001, count: 13915},
                       {source: 20, target: 120, value: 0.001, count: 18},
                       {source: 2, target: 120, value: 0.001, count: 201},
                       {source: 12, target: 120, value: 0.001, count: 263},
                       {source: 14, target: 120, value: 0.001, count: 848},
                       {source: 1, target: 120, value: 0.00154161332149514, count: 81461},
                       {source: 5, target: 120, value: 0.00244280071631718, count: 129081},
                       {source: 7, target: 120, value: 0.00709544085474199, count: 374933},
                       {source: 0, target: 120, value: 0.0096166460098821, count: 508157},
                       {source: 8, target: 120, value: 0.110967791237007, count: 5863693},
                       {source: 18, target: 3, value: 0.001, count: 2},
                       {source: 16, target: 3, value: 0.001, count: 38},
                       {source: 19, target: 3, value: 0.001, count: 44},
                       {source: 19, target: 67, value: 0.001, count: 491},
                       {source: 18, target: 67, value: 0.001, count: 579},
                       {source: 12, target: 121, value: 0.001, count: 260},
                       {source: 14, target: 121, value: 0.001, count: 1052},
                       {source: 0, target: 121, value: 0.001, count: 1508},
                       {source: 5, target: 121, value: 0.001, count: 2364},
                       {source: 7, target: 121, value: 0.001, count: 34334},
                       {source: 1, target: 121, value: 0.001, count: 46427},
                       {source: 8, target: 121, value: 0.0132477007231225, count: 700027},
                       {source: 32, target: 64, value: 0.001, count: 17},
                       {source: 7, target: 122, value: 0.001, count: 2},
                       {source: 0, target: 122, value: 0.001, count: 2},
                       {source: 5, target: 122, value: 0.001, count: 5},
                       {source: 8, target: 122, value: 0.001, count: 362}];

        var dependencyOptions = {
          width: ($(window).width() > Zipkin.Config.MAX_AGG_WINDOW_SIZE ? Zipkin.Config.MAX_AGG_GRAPHIC_WIDTH: Zipkin.Config.MIN_AGG_GRAPHIC_WIDTH) + 200
        };

        try {
          $('#loading-data').hide();
          var globalDependencies = new Zipkin.GlobalDependencies(nodes, links, dependencyOptions);
          globalDependencies.chart;
          $('#service-report').show();
          $('#global-dependency').show();
        } catch (e) {
          console.log(e);
          $('#loading-data').hide();
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