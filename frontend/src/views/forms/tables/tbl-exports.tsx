'use client';

// material-ui
import { ButtonBase, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// third-party
import { IconDeviceFloppy } from '@tabler/icons-react';
import { CSVLink } from 'react-csv';

// assets

// ==============================|| CSV Export ||============================== //

const CSVExport = ({ data, filename, headers }: any) => {
  const theme = useTheme();

  return (
    <Tooltip title="CSV Export" placement="left">
      <ButtonBase sx={{ mt: 0.5 }}>
        <CSVLink data={data} filename={filename} headers={headers}>
          <IconDeviceFloppy color={theme.palette.secondary.main} aria-label="Export CSV File" />
        </CSVLink>
      </ButtonBase>
    </Tooltip>
  );
};

export default CSVExport;
