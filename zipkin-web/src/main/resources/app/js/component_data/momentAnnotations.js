'use strict';

define([], function () {
  // A port of com.twitter.algebird.Moments functionality
  return function (moments) {
    var count = moments.m0;
    var mean = moments.m1;
    var variance = moments.m2 / count;
    var stddev = Math.sqrt(variance);
    var skewness = Math.sqrt(count) * moments.m3 / Math.pow(moments.m2, 1.5);
    var kurtosis = count * moments.m4 / Math.pow(moments.m2, 2) - 3;
    return {
      count: count,
      mean: mean,
      variance: variance,
      stddev: stddev,
      skewness: skewness,
      kurtosis: kurtosis
    };
  }
});
