'use client';

// material-ui

// assets
import HeightIcon from '@mui/icons-material/Height';
import MenuRoundedIcon from '@mui/icons-material/MenuRounded';
import MoreHorizTwoToneIcon from '@mui/icons-material/MoreHorizTwoTone';
import SearchIcon from '@mui/icons-material/Search';
import { Grid,
  IconButton,
  InputAdornment,
  Menu,
  MenuItem,
  Stack,
  TablePagination,
  TextField,
  useMediaQuery,
  Theme,
 } from '@mui/material';
import { useState } from 'react';

// types
import { MailListHeaderProps } from 'types/mail';

// ==============================|| MAIL LIST HEADER ||============================== //

const MailListHeader = ({
  search,
  handleSearch,
  length,
  rowsPerPage,
  page,
  handleChangePage,
  handleChangeRowsPerPage,
  handleDrawerOpen,
  handleDenseTable,
}: MailListHeaderProps) => {
  const matchDownSM = useMediaQuery((theme: Theme) => theme.breakpoints.down('md'));
  1;
  const [anchorEl, setAnchorEl] = useState<Element | (() => Element) | null | undefined>(null);
  const handleClickSort = (event: React.MouseEvent<HTMLButtonElement>) => {
    setAnchorEl(event.currentTarget);
  };
  const handleCloseSort = () => {
    setAnchorEl(null);
  };

  return (
    <Grid container alignItems="center" justifyContent="space-between">
      <Grid size="grow">
        <Stack direction="row" alignItems="center" justifyContent="flex-start" spacing={1.5}>
          <IconButton onClick={handleDrawerOpen} size="small" aria-label="mail">
            <MenuRoundedIcon fontSize="small" />
          </IconButton>
          <IconButton
            onClick={handleDenseTable}
            size="small"
            aria-label="click to size change of mail"
          >
            <HeightIcon fontSize="small" />
          </IconButton>
          <IconButton onClick={handleClickSort} size="small" aria-label="more options">
            <MoreHorizTwoToneIcon fontSize="small" />
          </IconButton>
          <Menu
            id="simple-menu"
            anchorEl={anchorEl}
            keepMounted
            open={Boolean(anchorEl)}
            onClose={handleCloseSort}
          >
            <MenuItem onClick={handleCloseSort}>Name</MenuItem>
            <MenuItem onClick={handleCloseSort}>Date</MenuItem>
            <MenuItem onClick={handleCloseSort}>Ratting</MenuItem>
            <MenuItem onClick={handleCloseSort}>Unread</MenuItem>
          </Menu>
          <TextField
            sx={{ display: { xs: 'block', sm: 'none' } }}
            fullWidth={matchDownSM}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            }}
            onChange={handleSearch}
            placeholder="Search Mail"
            value={search}
            size="small"
          />
        </Stack>
      </Grid>
      <Grid sx={{ display: { xs: 'none', sm: 'block' } }}>
        <Stack direction="row" alignItems="center" justifyContent="flex-end" spacing={1.5}>
          <TextField
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            }}
            onChange={handleSearch}
            placeholder="Search Mail"
            value={search}
            size="small"
          />
          {/* table pagination */}
          <TablePagination
            sx={{ '& .MuiToolbar-root': { pl: 1 } }}
            rowsPerPageOptions={[]}
            component="div"
            count={length}
            rowsPerPage={rowsPerPage}
            page={page}
            onPageChange={handleChangePage}
            onRowsPerPageChange={handleChangeRowsPerPage}
          />
        </Stack>
      </Grid>
    </Grid>
  );
};

export default MailListHeader;
