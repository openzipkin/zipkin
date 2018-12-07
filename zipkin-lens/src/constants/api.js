const { API_BASE } = process.env;

export const ZIPKIN_API = `${API_BASE || ''}/zipkin/api/v2`;
export const SERVICES = `${ZIPKIN_API}/services`;
export const SPANS = `${ZIPKIN_API}/spans`;
export const TRACES = `${ZIPKIN_API}/traces`;
export const TRACE = `${ZIPKIN_API}/trace`;
export const DEPENDENCIES = `${ZIPKIN_API}/dependencies`;
