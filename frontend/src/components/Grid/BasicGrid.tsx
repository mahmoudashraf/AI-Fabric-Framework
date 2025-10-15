// material-ui
import { Box, Grid } from '@mui/material';

// project imports
import Item from './GridItem';

// ===============================|| GRID - BASIC GRID ||=============================== //

export default function BasicGrid() {
  return (
    <Box sx={{ flexGrow: 1 }}>
      <Grid container spacing={2}>
        <Grid size={8}>
          <Item>xs=8</Item>
        </Grid>
        <Grid size={4}>
          <Item>xs=4</Item>
        </Grid>
        <Grid size={4}>
          <Item>xs=4</Item>
        </Grid>
        <Grid size={8}>
          <Item>xs=8</Item>
        </Grid>
      </Grid>
    </Box>
  );
}
