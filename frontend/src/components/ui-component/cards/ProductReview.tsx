'use client';

import DirectionsRunIcon from '@mui/icons-material/DirectionsRun';
import MoreHorizOutlinedIcon from '@mui/icons-material/MoreHorizOutlined';
import StarBorderTwoToneIcon from '@mui/icons-material/StarBorderTwoTone';
import StarTwoToneIcon from '@mui/icons-material/StarTwoTone';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';
import { Divider, Grid, Menu, MenuItem, Rating, Stack, Tooltip, Typography  } from '@mui/material';
import { format } from 'date-fns';
import { useState } from 'react';

// material-ui

// third-party

// project imports
import Avatar from '../extended/Avatar';

// assets

const avatarImage = '/assets/images/users';

// ==============================|| PRODUCT DETAILS - REVIEW ||============================== //

interface ReviewProps {
  avatar: string;
  date: Date | string;
  name: string;
  status: boolean;
  rating: number;
  review: string;
}

const ProductReview = ({ avatar, date, name, status, rating, review }: ReviewProps) => {
  const [anchorEl, setAnchorEl] = useState<Element | (() => Element) | null | undefined>(null);
  const handleClick = (event: React.MouseEvent<SVGSVGElement> | undefined) => {
    setAnchorEl(event?.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 4 }}>
        <Stack direction="row" alignItems="center" spacing={2}>
          <Avatar alt={name} src={avatar && `${avatarImage}/${avatar}`} />
          <Stack spacing={0.5}>
            <Stack spacing={1} direction="row" alignItems="center">
              <Typography
                variant="subtitle1"
                sx={{
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  display: 'block',
                }}
              >
                {name}
              </Typography>
              {status && (
                <Tooltip title="Purchased Verified">
                  <VerifiedUserIcon fontSize="small" sx={{ color: 'success.dark' }} />
                </Tooltip>
              )}
              {!status && (
                <Tooltip title="Goodwill">
                  <DirectionsRunIcon fontSize="small" sx={{ color: 'error.main' }} />
                </Tooltip>
              )}
            </Stack>
            <Typography variant="caption">{format(new Date(date), 'E, MMM d yyyy')}</Typography>
          </Stack>
        </Stack>
      </Grid>
      <Grid size={{ xs: 12, md: 8, lg: 9, xl: 10 }}>
        <Grid container alignItems="center" spacing={2}>
          <Grid size={11}>
            <Stack spacing={1}>
              <Rating
                size="small"
                name="simple-controlled"
                value={rating < 4 ? rating + 1 : rating}
                icon={<StarTwoToneIcon fontSize="inherit" />}
                emptyIcon={<StarBorderTwoToneIcon fontSize="inherit" />}
                precision={0.1}
                readOnly
              />
              <Typography variant="body2">{review}</Typography>
            </Stack>
          </Grid>
          <Grid size={1}>
            <Stack justifyContent="flex-end">
              <MoreHorizOutlinedIcon
                fontSize="small"
                aria-controls="menu-popular-card"
                aria-haspopup="true"
                onClick={handleClick}
                sx={{ color: 'grey.500', cursor: 'pointer' }}
              />
              <Menu
                id="menu-popular-card"
                anchorEl={anchorEl}
                keepMounted
                open={Boolean(anchorEl)}
                onClose={handleClose}
                variant="selectedMenu"
                anchorOrigin={{
                  vertical: 'bottom',
                  horizontal: 'right',
                }}
                transformOrigin={{
                  vertical: 'top',
                  horizontal: 'right',
                }}
              >
                <MenuItem onClick={handleClose}> Edit</MenuItem>
                <MenuItem onClick={handleClose}> Delete</MenuItem>
              </Menu>
            </Stack>
          </Grid>
        </Grid>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Divider />
      </Grid>
    </Grid>
  );
};

export default ProductReview;
