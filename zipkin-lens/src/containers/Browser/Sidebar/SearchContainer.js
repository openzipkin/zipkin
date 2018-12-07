/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

import { connect } from 'react-redux';

import Search from '../../../components/Browser/Sidebar/Search';
import { fetchSpans } from '../../../actions/spans-action';
import { fetchServices } from '../../../actions/services-action';

const mapStateToProps = state => ({
  spans: state.spans.spans,
  services: state.services.services,
});

const mapDispatchToProps = dispatch => ({
  fetchServices: () => dispatch(fetchServices()),
  fetchSpans: serviceName => dispatch(fetchSpans(serviceName)),
});

const SearchContainer = connect(
  mapStateToProps,
  mapDispatchToProps,
)(Search);

export default SearchContainer;
