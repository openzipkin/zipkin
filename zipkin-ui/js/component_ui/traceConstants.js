const CLIENT_SEND = 'cs';
const CLIENT_SEND_FRAGMENT = 'csf';
const CLIENT_RECEIVE = 'cr';
const CLIENT_RECEIVE_FRAGMENT = 'crf';
const SERVER_SEND = 'ss';
const SERVER_SEND_FRAGMENT = 'ssf';
const SERVER_RECEIVE = 'sr';
const SERVER_RECEIVE_FRAGMENT = 'srf';
const SERVER_ADDR = 'sa';
const CLIENT_ADDR = 'ca';
const WIRE_SEND = 'ws';
const WIRE_RECEIVE = 'wr';
const ERROR = 'error';
const LOCAL_COMPONENT = 'lc';
const CORE_CLIENT = [CLIENT_RECEIVE, CLIENT_RECEIVE_FRAGMENT, CLIENT_SEND, CLIENT_SEND_FRAGMENT];
const CORE_SERVER = [SERVER_RECEIVE, SERVER_RECEIVE_FRAGMENT, SERVER_SEND, SERVER_SEND_FRAGMENT];
const CORE_ADDRESS = [CLIENT_ADDR, SERVER_ADDR];
const CORE_WIRE = [WIRE_SEND, WIRE_RECEIVE];
const CORE_LOCAL = [LOCAL_COMPONENT];
const CORE_ANNOTATIONS = [...CORE_CLIENT, ...CORE_SERVER, ...CORE_WIRE, ...CORE_LOCAL];
export const Constants = {
  CLIENT_SEND,
  CLIENT_SEND_FRAGMENT,
  CLIENT_RECEIVE,
  CLIENT_RECEIVE_FRAGMENT,
  SERVER_SEND,
  SERVER_SEND_FRAGMENT,
  SERVER_RECEIVE,
  SERVER_RECEIVE_FRAGMENT,
  SERVER_ADDR,
  CLIENT_ADDR,
  CORE_CLIENT,
  CORE_SERVER,
  ERROR,
  LOCAL_COMPONENT,
  CORE_ADDRESS,
  CORE_WIRE,
  CORE_LOCAL,
  CORE_ANNOTATIONS
};

export const ConstantNames = {};
ConstantNames[CLIENT_SEND] = 'Client Send';
ConstantNames[CLIENT_SEND_FRAGMENT] = 'Client Send Fragment';
ConstantNames[CLIENT_RECEIVE] = 'Client Receive';
ConstantNames[CLIENT_RECEIVE_FRAGMENT] = 'Client Receive Fragment';
ConstantNames[SERVER_SEND] = 'Server Send';
ConstantNames[SERVER_SEND_FRAGMENT] = 'Server Send Fragment';
ConstantNames[SERVER_RECEIVE] = 'Server Receive';
ConstantNames[SERVER_RECEIVE_FRAGMENT] = 'Server Receive Fragment';
ConstantNames[CLIENT_ADDR] = 'Client Address';
ConstantNames[SERVER_ADDR] = 'Server Address';
ConstantNames[WIRE_SEND] = 'Wire Send';
ConstantNames[WIRE_RECEIVE] = 'Wire Receive';
ConstantNames[LOCAL_COMPONENT] = 'Local Component';
// Don't add ERROR to ConstantNames -- css coloring depends on constant name 'error'
