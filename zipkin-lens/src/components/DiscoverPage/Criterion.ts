/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import shortid from 'shortid';

type Criterion = {
  key: string;
  value: string;
  id: string; // for React key props.
};

export const newCriterion = (key = '', value = '') => {
  return {
    key,
    value,
    id: shortid.generate(),
  };
};

export default Criterion;
