/*
 * Copyright 2015-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
import PropTypes from 'prop-types';
import React, { useState, useCallback } from 'react';
import Badge from '@material-ui/core/Badge';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';

import ServiceFilterPopover from './ServiceFilterPopover';

const propTypes = {
  filters: PropTypes.arrayOf(PropTypes.string).isRequired,
};

const ServiceFilter = ({ filters, ...props }) => {
  const [anchorEl, setAnchorEl] = useState(null);

  const handleButtonClick = useCallback(event => setAnchorEl(event.currentTarget), []);

  const handleMenuClose = useCallback(() => setAnchorEl(null), []);

  return (
    <React.Fragment>
      <Badge
        color="secondary"
        badgeContent={`+${filters.length - 1}`}
        invisible={filters.length <= 1}
      >
        <Button onClick={handleButtonClick} data-test="button">
          <Box component="span" className="fas fa-filter" />
          &nbsp;
          {`${filters.length === 0 ? 'Filter' : filters[0]}`}
        </Button>
      </Badge>
      <ServiceFilterPopover
        open={Boolean(anchorEl)}
        anchorEl={anchorEl}
        onClose={handleMenuClose}
        filters={filters}
        {...props}
      />
    </React.Fragment>
  );
};

ServiceFilter.propTypes = propTypes;

export default ServiceFilter;
