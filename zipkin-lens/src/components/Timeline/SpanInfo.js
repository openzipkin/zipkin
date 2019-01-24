import PropTypes from 'prop-types';
import React from 'react';
import moment from 'moment';
import ReactTable from 'react-table';

import ServiceNameBadge from '../Common/ServiceNameBadge';
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
          ? span.serviceNames.map(serviceName => (
            <ServiceNameBadge
              key={serviceName}
              serviceName={serviceName}
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
      data={span.tags}
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
