import {
  Box,
  CircularProgress,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import type { ReactNode } from 'react'

export interface DataColumn<T> {
  key: string
  header: string
  align?: 'left' | 'right' | 'center'
  render: (row: T) => ReactNode
}

export function DataTable<T>({
  columns,
  rows,
  getRowKey,
  loading,
  empty,
}: {
  columns: DataColumn<T>[]
  rows: T[]
  getRowKey: (row: T) => string
  loading?: boolean
  empty?: ReactNode
}) {
  return (
    <TableContainer component={Paper} sx={{ overflowX: 'auto' }}>
      <Table>
        <TableHead>
          <TableRow>
            {columns.map((column) => (
              <TableCell key={column.key} align={column.align}>
                <Typography variant="caption" color="text.secondary">
                  {column.header}
                </Typography>
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {loading ? (
            <TableRow>
              <TableCell colSpan={columns.length}>
                <Box sx={{ py: 5, display: 'grid', placeItems: 'center' }}>
                  <CircularProgress size={24} />
                </Box>
              </TableCell>
            </TableRow>
          ) : rows.length === 0 ? (
            <TableRow>
              <TableCell colSpan={columns.length}>{empty ?? <Typography color="text.secondary">No records yet.</Typography>}</TableCell>
            </TableRow>
          ) : (
            rows.map((row) => (
              <TableRow key={getRowKey(row)} hover>
                {columns.map((column) => (
                  <TableCell key={column.key} align={column.align}>
                    {column.render(row)}
                  </TableCell>
                ))}
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
