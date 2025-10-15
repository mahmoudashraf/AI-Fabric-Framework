'use client';

// material-ui
import { Box, Stack } from '@mui/material';
import { DataGrid, GridColDef, GridRowId } from '@mui/x-data-grid';
import { useState, useEffect } from 'react';

// project import
import CardSecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import CSVExport from 'views/forms/tables/tbl-exports';

export interface DataRowModel {
  id: GridRowId;
  [price: string]: number | string;
}

export interface GridData {
  columns: GridColDef[];
  rows: DataRowModel[];
}

function useData(rowLength: number, columnLength: number) {
  const [data, setData] = useState<GridData>({ columns: [], rows: [] });

  useEffect(() => {
    const rows: DataRowModel[] = [];

    for (let i = 0; i < rowLength; i += 1) {
      const row: DataRowModel = {
        id: i,
      };

      for (let j = 1; j <= columnLength; j += 1) {
        row[`price${j}M`] = `${i.toString()}, ${j} `;
      }

      rows.push(row);
    }

    const columns: GridColDef[] = [];

    for (let j = 1; j <= columnLength; j += 1) {
      columns.push({ field: `price${j}M`, headerName: `${j}M` });
    }

    setData({
      rows,
      columns,
    });
  }, [rowLength, columnLength]);

  return data;
}

// ==============================|| COLUMN VIRTUALIZATION DATA GRID ||============================== //

export default function Virtualization() {
  const data = useData(100, 1000);
  let headers: any = [];
  data.columns.map(item => {
    return headers.push({ label: item.headerName, key: item.field });
  });

  return (
    <MainCard
      content={false}
      title="Column Virtualization"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport
            data={data.rows}
            filename={'column-virtualization-data-grid-table.csv'}
            header={headers}
          />
          <CardSecondaryAction link="https://mui.com/x/react-data-grid/virtualization/#column-virtualization" />
        </Stack>
      }
    >
      <Box sx={{ height: 670, width: '100%' }}>
        <DataGrid hideFooter {...data} columnBufferPx={2} />
      </Box>
    </MainCard>
  );
}
