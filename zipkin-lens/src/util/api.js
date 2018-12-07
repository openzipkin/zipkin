import QueryString from 'query-string';

export const buildQueryParameters = (params) => {
  const cleaned = {};

  Object.keys(params).forEach((key) => {
    if (params[key]) {
      cleaned[key] = params[key];
    }
  });

  return QueryString.stringify(cleaned);
};
