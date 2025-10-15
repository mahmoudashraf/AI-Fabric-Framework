// material-ui
import { Box, Grid } from '@mui/material';

// project imports
import Item from './GridItem';

// ===============================|| GRID - COLUMN ||=============================== //

export default function ColumnsGrid() {
  return (
    <Box sx={{ flexGrow: 1 }}>
      <Grid container spacing={2} columns={16}>
        <Grid size={8}>
          <Item>xs=8</Item>
        </Grid>
        <Grid size={8}>
          <Item>xs=8</Item>
        </Grid>
      </Grid>
    </Box>
  );
}
