/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import PropTypes from 'prop-types';
import React from 'react';
import moment from 'moment';
import ReactTable from 'react-table';

import ServiceNameBadge from '../Common/ServiceNameBadge';
import { getServiceNameColor } from '../../util/color';
import { detailedSpanPropTypes } from '../../prop-types';

const propTypes = {
  serviceNameColumnWidth: PropTypes.number.isRequired,
  span: detailedSpanPropTypes.isRequired,
};

const renderIdData = (key, value) => {
  if (!value) {
    return null;
  }
  return (
    <div className="timeline-span-data__id-data">
      <div className="timeline-span-data__id-data-key">
        {key}
        :&nbsp;
      </div>
      <div className="timeline-span-data__id-data-value">
        {value}
      </div>
    </div>
  );
};

const renderData = span => (
  <div className="timeline-span-data__content">
    <div
      className="timeline-span-data__title"
      style={{
        borderColor: getServiceNameColor(span.serviceName),
      }}
    >
      {`${span.serviceName}: ${span.spanName}`}
    </div>
    <div className="timeline-span-data__aka-badges">
      {
        span.serviceNames
          ? span.serviceNames.map(serviceName => (
            <ServiceNameBadge
              key={serviceName}
              serviceName={serviceName}
              className="timeline-span-data__aka-badge"
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
            // moment.js only supports millisecond precision, however our timestamps have
            // microsecond precision. So we use moment.js to generate the human readable time
            // with just milliseconds and then append the last 3 digits of the timestamp
            // which are the microseconds.
            // NOTE: a.timestamp % 1000 would save a string conversion but drops leading zeros.
            duration:
              moment(a.timestamp / 1000).format('MM/DD HH:mm:ss.SSS') + a.timestamp.toString().slice(-3),
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
      data={span.tags}
      columns={
        [
          { Header: 'Key', accessor: 'key' },
          // `white-space: normal` makes long text wrap correctly.
          { Header: 'Value', accessor: 'value', style: { 'white-space': 'normal' } },
        ]
      }
    />
    <div className="timeline-span-data__ids">
      {renderIdData('Span ID', span.spanId)}
      {renderIdData('Parent ID', span.parentId)}
    </div>
  </div>
);

const TimelineSpanData = ({ span, serviceNameColumnWidth }) => (
  <div className="timeline-span-data">
    <div
      className="timeline-span-data__left-container"
      style={{ width: `${serviceNameColumnWidth * 100}%` }}
    >
      <span
        className="timeline-span-data__depth-marker"
        style={{
          left: `${span.depth * 14}px`,
          background: `${getServiceNameColor(span.serviceName)}`,
        }}
      />
    </div>
    <div
      className="timeline-span-data__right-container"
      style={{
        left: `${(serviceNameColumnWidth) * 100}%`,
        width: `${(1 - serviceNameColumnWidth) * 100}%`,
      }}
    >
      {renderData(span)}
    </div>
  </div>

);

TimelineSpanData.propTypes = propTypes;

export default TimelineSpanData;
