'use client';

import React from 'react';

// Context-based snackbar implementation
import SnackbarContext from './SnackbarContext';

// ==============================|| SNACKBAR WRAPPER ||============================== //

/**
 * Snackbar wrapper using pure Context API implementation
 */
const SnackbarWrapper: React.FC = () => {
  return <SnackbarContext />;
};

export default SnackbarWrapper;
