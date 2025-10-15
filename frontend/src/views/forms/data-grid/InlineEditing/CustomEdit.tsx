'use client';

// material-ui
import { Box, Rating, Stack } from '@mui/material';
import { unstable_useEnhancedEffect as useEnhancedEffect } from '@mui/utils';
import { DataGrid, GridRenderCellParams, GridColDef, useGridApiContext } from '@mui/x-data-grid';
import * as React from 'react';

// project import
import CardSecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

function renderRating(params: GridRenderCellParams<any, number>) {
  return <Rating readOnly value={params.value ?? null} />;
}

function RatingEditInputCell(props: GridRenderCellParams<any, number>) {
  const { id, value, field, hasFocus } = props;
  const apiRef = useGridApiContext();
  const ref = React.useRef<HTMLSpanElement>(null);

  const handleChange = (_event: React.SyntheticEvent, newValue: number | null) => {
    apiRef.current.setEditCellValue({ id, field, value: newValue });
  };

  useEnhancedEffect(() => {
    if (hasFocus && ref.current) {
      const input = ref.current.querySelector<HTMLInputElement>(`input[value="${value}"]`);
      input?.focus();
    }
  }, [hasFocus, value]);

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', pr: 2 }}>
      <Rating component="span" ref={ref} name="rating" precision={1} value={value ?? 0} onChange={handleChange} />
    </Box>
  );
}

const renderRatingEditInputCell: GridColDef['renderCell'] = params => {
  return <RatingEditInputCell {...params} />;
};

// ==============================|| CUSTOM EDIT DATA GRID ||============================== //

export default function CustomEditComponent() {
  let headers: any = [];
  columns.map(item => {
    return headers.push({ label: item.headerName, key: item.field });
  });

  return (
    <MainCard
      content={false}
      title="Customize Editing"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport data={rows} filename={'custom-edit-data-grid-table.csv'} header={headers} />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/editing/#create-your-own-edit-component" />
        </Stack>
      }
    >
      <Box sx={{ width: '100%' }}>
        <DataGrid hideFooter autoHeight rows={rows} columns={columns} />
      </Box>
    </MainCard>
  );
}

const columns = [
  {
    field: 'places',
    headerName: 'Places',
    flex: 0.75,
    minWidth: 120,
  },
  {
    field: 'rating',
    headerName: 'Rating',
    renderCell: renderRating,
    renderEditCell: renderRatingEditInputCell,
    editable: true,
    flex: 1,
    minWidth: 120,
  },
];

const rows = [
  { id: 1, places: 'Barcelona', rating: 5 },
  { id: 2, places: 'Rio de Janeiro', rating: 4 },
  { id: 3, places: 'London', rating: 3 },
  { id: 4, places: 'New York', rating: 2 },
];
