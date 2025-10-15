'use client';

import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import {
  Box,
  Collapse,
  IconButton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import React from 'react';

// material-ui

// project imports
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';

import { header } from './tbl-basic';
import CSVExport from './tbl-exports';

// assets

// table data
type BasicTableData = {
  name: string;
  calories: number;
  fat: number;
  carbs: number;
  protein: number;
  price: number;
  history?: { date: string; customerId: string; amount: number }[];
};
function createData(
  name: string,
  calories: number,
  fat: number,
  carbs: number,
  protein: number,
  price: number
) {
  return {
    name,
    calories,
    fat,
    carbs,
    protein,
    price,
    history: [
      { date: '2020-01-05', customerId: '11091700', amount: 3 },
      { date: '2020-01-02', customerId: 'Anonymous', amount: 1 },
    ],
  };
}

function Row({ row }: { row: BasicTableData }) {
  const theme = useTheme();
  const [open, setOpen] = React.useState(false);

  return (
    <>
      <TableRow hover sx={{ '& > *': { borderBottom: 'unset' } }}>
        <TableCell sx={{ pl: 3, width: 74 }}>
          <IconButton aria-label="expand row" size="small" onClick={() => setOpen(!open)}>
            {open ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
          </IconButton>
        </TableCell>
        <TableCell component="th" scope="row">
          {row.name}
        </TableCell>
        <TableCell align="right">{row.calories}</TableCell>
        <TableCell align="right">{row.fat}</TableCell>
        <TableCell align="right">{row.carbs}</TableCell>
        <TableCell align="right" sx={{ pr: 3 }}>
          {row.protein}
        </TableCell>
      </TableRow>
      <TableRow>
        <TableCell sx={{ pb: 0, pt: 0 }} colSpan={6}>
          <Collapse in={open} timeout="auto" unmountOnExit>
            {open && (
              <Box sx={{ margin: 1 }}>
                <TableContainer>
                  <SubCard
                    sx={{ bgcolor: theme.palette.mode === 'dark' ? 'dark.800' : 'grey.50', mb: 2 }}
                    title="History"
                    content={false}
                    secondary={
                      <Stack direction="row" spacing={2} alignItems="center">
                        <CSVExport
                          data={row.history?.map((historyRow: any) => historyRow)}
                          filename={'collapse-table.csv'}
                          header={header}
                        />
                      </Stack>
                    }
                  >
                    <Table size="small" aria-label="purchases">
                      <TableHead>
                        <TableRow>
                          <TableCell>Date</TableCell>
                          <TableCell>Customer</TableCell>
                          <TableCell align="right">Amount</TableCell>
                          <TableCell align="right">Total price ($)</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {row.history?.map(
                          (historyRow: { date: string; customerId: string; amount: number }) => (
                            <TableRow hover key={historyRow.date}>
                              <TableCell component="th" scope="row">
                                {historyRow.date}
                              </TableCell>
                              <TableCell>{historyRow.customerId}</TableCell>
                              <TableCell align="right">{historyRow.amount}</TableCell>
                              <TableCell align="right">
                                {Math.round(historyRow.amount * row.price * 100) / 100}
                              </TableCell>
                            </TableRow>
                          )
                        )}
                      </TableBody>
                    </Table>
                  </SubCard>
                </TableContainer>
              </Box>
            )}
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
}

const rows = [
  createData('Frozen yoghurt', 159, 6.0, 24, 4.0, 3.99),
  createData('Ice cream sandwich', 237, 9.0, 37, 4.3, 4.99),
  createData('Eclair', 262, 16.0, 24, 6.0, 3.79),
  createData('Cupcake', 305, 3.7, 67, 4.3, 2.5),
  createData('Gingerbread', 356, 16.0, 49, 3.9, 1.5),
];

// ==============================|| TABLE - COLLAPSIBLE ||============================== //

function TableCollapsible() {
  let newRow: any = [];
  rows.forEach(element => {
    newRow.push({
      ...element,
      history: null,
    });
  });
  return (
    <MainCard
      content={false}
      title="Collapsible Table"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          <CSVExport data={newRow} filename={'collapse-table.csv'} header={header} />
          <SecondaryAction link="https://next.material-ui.com/components/tables/" />
        </Stack>
      }
    >
      {/* table */}
      <TableContainer>
        <Table aria-label="collapsible table">
          <TableHead>
            <TableRow>
              <TableCell sx={{ pl: 3 }} />
              <TableCell>Dessert (100g serving)</TableCell>
              <TableCell align="right">Calories</TableCell>
              <TableCell align="right">Fat&nbsp;(g)</TableCell>
              <TableCell align="right">Carbs&nbsp;(g)</TableCell>
              <TableCell sx={{ pr: 3 }} align="right">
                Protein&nbsp;(g)
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map(row => (
              <Row key={row.name} row={row} />
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </MainCard>
  );
}

export default TableCollapsible;
