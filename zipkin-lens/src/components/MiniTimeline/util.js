export const getGraphHeight = (numSpans) => {
  if (numSpans <= 1) {
    return 0;
  }
  if (numSpans <= 14) {
    return numSpans * 5;
  }
  return 75;
};

export const getGraphLineHeight = (numSpans) => {
  if (numSpans <= 1) {
    return 0;
  }
  if (numSpans <= 14) {
    return 5;
  }
  return Math.max(75 / numSpans, 1);
};
