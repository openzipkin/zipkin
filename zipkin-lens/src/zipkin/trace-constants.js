/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
const CLIENT_SEND = 'cs';
const CLIENT_SEND_FRAGMENT = 'csf';
const CLIENT_RECEIVE = 'cr';
const CLIENT_RECEIVE_FRAGMENT = 'crf';
const MESSAGE_SEND = 'ms';
const MESSAGE_RECEIVE = 'mr';
const SERVER_SEND = 'ss';
const SERVER_SEND_FRAGMENT = 'ssf';
const SERVER_RECEIVE = 'sr';
const SERVER_RECEIVE_FRAGMENT = 'srf';
const SERVER_ADDR = 'sa';
const CLIENT_ADDR = 'ca';
const MESSAGE_ADDR = 'ma';
const WIRE_SEND = 'ws';
const WIRE_RECEIVE = 'wr';
const LOCAL_COMPONENT = 'lc';

export const ConstantNames = {};
ConstantNames[CLIENT_SEND] = 'Client Send';
ConstantNames[CLIENT_SEND_FRAGMENT] = 'Client Send Fragment';
ConstantNames[CLIENT_RECEIVE] = 'Client Receive';
ConstantNames[CLIENT_RECEIVE_FRAGMENT] = 'Client Receive Fragment';
ConstantNames[MESSAGE_SEND] = 'Producer Send';
ConstantNames[MESSAGE_RECEIVE] = 'Consumer Receive';
ConstantNames[SERVER_SEND] = 'Server Send';
ConstantNames[SERVER_SEND_FRAGMENT] = 'Server Send Fragment';
ConstantNames[SERVER_RECEIVE] = 'Server Receive';
ConstantNames[SERVER_RECEIVE_FRAGMENT] = 'Server Receive Fragment';
ConstantNames[CLIENT_ADDR] = 'Client Address';
ConstantNames[MESSAGE_ADDR] = 'Broker Address';
ConstantNames[SERVER_ADDR] = 'Server Address';
ConstantNames[WIRE_SEND] = 'Wire Send';
ConstantNames[WIRE_RECEIVE] = 'Wire Receive';
ConstantNames[LOCAL_COMPONENT] = 'Local Component';
// Don't add ERROR to ConstantNames -- css coloring depends on constant name 'error'
