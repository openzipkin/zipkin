/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
import { t } from '@lingui/macro';
import { useLingui } from '@lingui/react';
import PropTypes from 'prop-types';
import React, { useState, useCallback } from 'react';
import { faFilter } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import Badge from '@material-ui/core/Badge';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';

import ServiceFilterPopover from './ServiceFilterPopover';

const propTypes = {
  filters: PropTypes.arrayOf(PropTypes.string).isRequired,
};

const ServiceFilter = ({ filters, ...props }) => {
  const [anchorEl, setAnchorEl] = useState(null);
  const { i18n } = useLingui();

  const handleButtonClick = useCallback(
    (event) => setAnchorEl(event.currentTarget),
    [],
  );

  const handleMenuClose = useCallback(() => setAnchorEl(null), []);

  return (
    <>
      <Badge
        color="secondary"
        badgeContent={`+${filters.length - 1}`}
        invisible={filters.length <= 1}
        data-testid="badge"
      >
        <Button onClick={handleButtonClick} data-testid="button">
          <FontAwesomeIcon icon={faFilter} />
          <Box ml={0.2} data-testid="button-text">
            {`${filters.length === 0 ? i18n._(t`Filter`) : filters[0]}`}
          </Box>
        </Button>
      </Badge>
      <ServiceFilterPopover
        open={Boolean(anchorEl)}
        anchorEl={anchorEl}
        onClose={handleMenuClose}
        filters={filters}
        {...props}
      />
    </>
  );
};

ServiceFilter.propTypes = propTypes;

export default ServiceFilter;
