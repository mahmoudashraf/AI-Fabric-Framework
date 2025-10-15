'use client';

// material-ui
import { Box, Stack } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { DataGrid, GridToolbarQuickFilter, GridLogicOperator } from '@mui/x-data-grid';
import { useDemoData } from '@mui/x-data-grid-generator';
import { useMemo } from 'react';

// project import
import CardSecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

function QuickSearchToolbar() {
  const theme = useTheme();
  return (
    <Box
      sx={{
        pb: 0,
        pl: 3,
        pr: 2,
        pt: 2,
        '& .MuiFormControl-root > .MuiInputBase-root': {
          p: 0.6,
          border: `1px solid ${theme.palette.grey[300]}`,
          borderRadius: 2,
          background: theme.palette.grey[50],
        },
        '& .MuiFormControl-root > .MuiInputBase-root:after': {
          display: 'none',
        },
        '& .MuiFormControl-root > .MuiInputBase-root:before': {
          display: 'none',
        },
        '& .MuiFormControl-root > .Mui-focused': {
          border: `1px solid ${theme.palette.primary.main}`,
        },
      }}
    >
      <GridToolbarQuickFilter
        quickFilterParser={(searchInput: string) =>
          searchInput
            .split(',')
            .map(value => value.trim())
            .filter(value => value !== '')
        }
      />
    </Box>
  );
}

// ==============================|| PARSING VALUES DATA GRID ||============================== //

export default function ParsingValues() {
  const VISIBLE_FIELDS = ['name', 'rating', 'country', 'dateCreated', 'isAdmin', 'email', 'phone'];
  const { data } = useDemoData({
    dataSet: 'Employee',
    visibleFields: VISIBLE_FIELDS,
    rowLength: 100,
  });

  // Otherwise filter will be applied on fields such as the hidden column id
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const columns = useMemo(
    () => data.columns.filter(column => VISIBLE_FIELDS.includes(column.field)),
    [data.columns]
  );

  let headers: any = [];
  columns.map(item => {
    return headers.push({ label: item.headerName, key: item.field });
  });

  return (
    <MainCard
      content={false}
      title="Parsing Values"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport
            data={data.rows}
            filename={'parsing-value-data-grid-table.csv'}
            header={headers}
          />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/filtering/quick-filter/#parsing-values" />
        </Stack>
      }
    >
      <Box sx={{ width: '100%' }}>
        <DataGrid
          {...data}
          columns={columns}
          autoHeight
          initialState={{
            ...data.initialState,
            pagination: {
              paginationModel: {
                pageSize: 5,
              },
            },
            filter: {
              ...data.initialState?.filter,
              filterModel: {
                items: [],
                quickFilterLogicOperator: GridLogicOperator.Or,
              },
            },
          }}
          hideFooterSelectedRowCount
          slots={{ toolbar: QuickSearchToolbar }}
        />
      </Box>
    </MainCard>
  );
}
