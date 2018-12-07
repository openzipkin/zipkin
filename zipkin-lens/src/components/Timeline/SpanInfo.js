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

import PropTypes from 'prop-types';
import React from 'react';
import moment from 'moment';
import ReactTable from 'react-table';

import Badge from '../Common/Badge';
import { getServiceNameColor } from '../../util/color';

const propTypes = {
  serviceNameColumnWidth: PropTypes.number.isRequired,
  span: PropTypes.shape({}).isRequired,
};

const renderInfo = span => (
  <div className="timeline__span-info-info">
    <div
      className="timeline__span-info-info-title"
      style={{
        borderColor: getServiceNameColor(span.serviceName),
      }}
    >
      {`${span.serviceName}: ${span.spanName}`}
    </div>
    <div className="timeline__span-info-info-aka">
      {
        span.serviceNames
          ? span.serviceNames.split(',').map(serviceName => (
            <Badge
              key={serviceName}
              value={serviceName}
              text={serviceName}
              className="timeline__span-info-info-aka-badge"
            />
          ))
          : null
      }
    </div>
    <ReactTable
      showPagination={false}
      minRows={0 /* Hide empty rows */}
      data={
        span.annotations.map(a => (
          {
            duration: moment(a.timestamp / 1000).format('MM/DD HH:mm:ss:SSS'),
            relativeTime: a.relativeTime,
            annotation: a.value,
            address: a.endpoint,
          }
        ))
      }
      columns={
        [
          { Header: 'Date Time', accessor: 'duration' },
          { Header: 'Relative Time', accessor: 'relativeTime' },
          { Header: 'Annotation', accessor: 'annotation' },
          { Header: 'Address', accessor: 'address' },
        ]
      }
    />
    <ReactTable
      showPagination={false}
      minRows={0 /* Hide empty rows */}
      data={span.binaryAnnotations}
      columns={
        [
          { Header: 'Key', accessor: 'key' },
          { Header: 'Value', accessor: 'value' },
        ]
      }
    />
  </div>
);

const SpanInfo = ({ span, serviceNameColumnWidth }) => (
  <div className="timeline__span-info">
    <div
      className="timeline__span-info-left"
      style={{ width: `${serviceNameColumnWidth * 100}%` }}
    >
      <span
        className="timeline__span-info-left-depth-marker"
        style={{
          left: `${span.depth * 14}px`,
          background: `${getServiceNameColor(span.serviceName)}`,
        }}
      />
    </div>
    <div
      className="timeline__span-info-right"
      style={{
        left: `${(serviceNameColumnWidth) * 100}%`,
        width: `${(1 - serviceNameColumnWidth) * 100}%`,
      }}
    >
      {renderInfo(span)}
    </div>
  </div>

);

SpanInfo.propTypes = propTypes;

export default SpanInfo;
