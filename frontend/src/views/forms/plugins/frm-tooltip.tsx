'use client';

import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import { Button,
  ClickAwayListener,
  Fab,
  Fade,
  Grid,
  IconButton,
  Typography,
  Zoom,
 } from '@mui/material';
import { styled } from '@mui/material/styles';
import MuiTooltip, { tooltipClasses, TooltipProps } from '@mui/material/Tooltip';
import React from 'react';

// material-ui

// project imports
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';

// assets

// tooltip customization
const LightTooltip = styled(({ className, ...other }: TooltipProps) => (
  <MuiTooltip {...other} classes={{ popper: (className || '') as string }} />
))(({ theme }) => ({
  [`& .${tooltipClasses.tooltip}`]: {
    backgroundColor: theme.palette.common.white,
    color: 'rgba(0, 0, 0, 0.87)',
    boxShadow: theme.shadows[1],
    fontSize: 11,
  },
}));

const BootstrapTooltip = styled(({ className, ...other }: TooltipProps) => (
  <MuiTooltip {...other} arrow classes={{ popper: (className || '') as string }} />
))(({ theme }) => ({
  [`& .${tooltipClasses.arrow}`]: {
    color: theme.palette.mode === 'light' ? theme.palette.common.black : theme.palette.common.white,
  },
  [`& .${tooltipClasses.tooltip}`]: {
    backgroundColor:
      theme.palette.mode === 'light' ? theme.palette.common.black : theme.palette.common.white,
  },
}));

const HtmlTooltip = styled(({ className, ...other }: TooltipProps) => (
  <MuiTooltip {...other} classes={{ popper: (className || '') as string }} />
))(({ theme }) => ({
  [`& .${tooltipClasses.tooltip}`]: {
    backgroundColor: '#f5f5f9',
    color: 'rgba(0, 0, 0, 0.87)',
    maxWidth: 220,
    fontSize: theme.typography.pxToRem(12),
    border: '1px solid #dadde9',
  },
}));

const CustomWidthTooltip = styled(({ className, ...other }: TooltipProps) => (
  <MuiTooltip {...other} classes={{ popper: (className || '') as string }} />
))({
  [`& .${tooltipClasses.tooltip}`]: {
    maxWidth: 500,
  },
});

const NoMaxWidthTooltip = styled(({ className, ...other }: TooltipProps) => (
  <MuiTooltip {...other} classes={{ popper: (className || '') as string }} />
))({
  [`& .${tooltipClasses.tooltip}`]: {
    maxWidth: 'none',
  },
});

// ==============================|| PLUGIN - TOOLTIP ||============================== //

function Tooltip() {
  const [open, setOpen] = React.useState(false);
  const handleTooltipClose = () => {
    setOpen(false);
  };

  const handleTooltipOpen = () => {
    setOpen(true);
  };

  const longText = `
Aliquam eget finibus ante, non facilisis lectus. Sed vitae dignissim est, vel aliquam tellus.
Praesent non nunc mollis, fermentum neque at, semper arcu.
Nullam eget est sed sem iaculis gravida eget vitae justo.`;

  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12 }}>
        <MainCard title="Simple Tooltips">
          <Grid container spacing={1} alignItems="center">
            <Grid>
              <MuiTooltip title="Delete">
                <IconButton aria-label="delete" size="large">
                  <DeleteIcon />
                </IconButton>
              </MuiTooltip>
            </Grid>
            <Grid>
              <MuiTooltip title="Add" aria-label="add">
                <Fab color="primary" sx={{ m: 2 }}>
                  <AddIcon />
                </Fab>
              </MuiTooltip>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <MainCard title="Customized Tooltip">
          <Grid container spacing={1} alignItems="center">
            <Grid>
              <LightTooltip title="Add">
                <Button>Light</Button>
              </LightTooltip>
            </Grid>
            <Grid>
              <BootstrapTooltip title="Add">
                <Button>Bootstrap</Button>
              </BootstrapTooltip>
            </Grid>
            <Grid>
              <HtmlTooltip
                title={
                  <>
                    <Typography color="inherit">Tooltip with HTML</Typography>
                    <em>And here&apos;s</em>{' '}
                    <Typography variant="subtitle1" component="span">
                      some
                    </Typography>{' '}
                    <u>amazing content</u>. it&apos;s very engaging. Right?
                  </>
                }
              >
                <Button>HTML</Button>
              </HtmlTooltip>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <MainCard title="Arrow Tooltips">
          <Grid container spacing={1} alignItems="center">
            <Grid>
              <MuiTooltip title="Add" arrow>
                <Button>Arrow</Button>
              </MuiTooltip>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>

      <Grid size={{ xs: 12 }}>
        <MainCard title="Delay Tooltips">
          <Grid container spacing={1} alignItems="center">
            <Grid>
              <MuiTooltip title="Add" enterDelay={500} leaveDelay={200}>
                <Button>[500ms, 200ms]</Button>
              </MuiTooltip>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <MainCard title="Disabled Tooltips">
          <Grid container spacing={1} alignItems="center">
            <Grid>
              <MuiTooltip title="You Don't have permission to do this">
                <span>
                  <Button disabled>A Disabled Button</Button>
                </span>
              </MuiTooltip>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <MainCard title="Disable Interactive Tooltips">
          <Grid container spacing={1} alignItems="center">
            <Grid>
              <MuiTooltip title="Add" disableInteractive>
                <Button>Disable Interactive</Button>
              </MuiTooltip>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>

      <Grid size={{ xs: 12 }}>
        <MainCard title="Triggers/Controlled Tooltips">
          <Grid container spacing={1} alignItems="center">
            <Grid>
              <MuiTooltip disableFocusListener title="Add">
                <Button>Hover or touch</Button>
              </MuiTooltip>
            </Grid>
            <Grid>
              <MuiTooltip disableFocusListener disableTouchListener title="Add">
                <Button>Hover</Button>
              </MuiTooltip>
            </Grid>
            <Grid>
              <ClickAwayListener onClickAway={handleTooltipClose}>
                <div>
                  <MuiTooltip
                    PopperProps={{
                      disablePortal: true,
                    }}
                    onClose={handleTooltipClose}
                    open={open}
                    disableFocusListener
                    disableHoverListener
                    disableTouchListener
                    title="Add"
                  >
                    <Button onClick={handleTooltipOpen}>Click</Button>
                  </MuiTooltip>
                </div>
              </ClickAwayListener>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <MainCard title="Transitions Tooltips">
          <Grid container spacing={1} alignItems="center">
            <Grid>
              <MuiTooltip title="Add">
                <Button>Grow</Button>
              </MuiTooltip>
            </Grid>
            <Grid>
              <MuiTooltip TransitionComponent={Fade as any} TransitionProps={{ timeout: 600 }} title="Add">
                <Button>Fade</Button>
              </MuiTooltip>
            </Grid>
            <Grid>
              <MuiTooltip TransitionComponent={Zoom as any} title="Add">
                <Button>Zoom</Button>
              </MuiTooltip>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <MainCard title="Variable Width Tooltips">
          <Grid container spacing={1} alignItems="center">
            <Grid>
              <MuiTooltip title={longText}>
                <Button sx={{ m: 1 }}>Default Width [300px]</Button>
              </MuiTooltip>
            </Grid>
            <Grid>
              <CustomWidthTooltip title={longText}>
                <Button sx={{ m: 1 }}>Custom Width [500px]</Button>
              </CustomWidthTooltip>
            </Grid>
            <Grid>
              <NoMaxWidthTooltip title={longText}>
                <Button sx={{ m: 1 }}>No wrapping</Button>
              </NoMaxWidthTooltip>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <MainCard title="Positioned Tooltips">
          <Grid container justifyContent="center">
            <Grid>
              <MuiTooltip title="Add" placement="top-start">
                <Button>top-start</Button>
              </MuiTooltip>
            </Grid>
            <Grid>
              <MuiTooltip title="Add" placement="top">
                <Button>top</Button>
              </MuiTooltip>
            </Grid>
            <Grid>
              <MuiTooltip title="Add" placement="top-end">
                <Button>top-end</Button>
              </MuiTooltip>
            </Grid>
          </Grid>
          <Grid container justifyContent="center">
            <Grid size={6}>
              <MuiTooltip title="Add" placement="left-start">
                <Button>left-start</Button>
              </MuiTooltip>
              <br />
              <MuiTooltip title="Add" placement="left">
                <Button>left</Button>
              </MuiTooltip>
              <br />
              <MuiTooltip title="Add" placement="left-end">
                <Button>left-end</Button>
              </MuiTooltip>
            </Grid>
            <Grid container size={6} alignItems="flex-end" direction="column">
              <Grid>
                <MuiTooltip title="Add" placement="right-start">
                  <Button>right-start</Button>
                </MuiTooltip>
              </Grid>
              <Grid>
                <MuiTooltip title="Add" placement="right">
                  <Button>right</Button>
                </MuiTooltip>
              </Grid>
              <Grid>
                <MuiTooltip title="Add" placement="right-end">
                  <Button>right-end</Button>
                </MuiTooltip>
              </Grid>
            </Grid>
          </Grid>
          <Grid container justifyContent="center">
            <Grid>
              <MuiTooltip title="Add" placement="bottom-start">
                <Button>bottom-start</Button>
              </MuiTooltip>
              <MuiTooltip title="Add" placement="bottom">
                <Button>bottom</Button>
              </MuiTooltip>
              <MuiTooltip title="Add" placement="bottom-end">
                <Button>bottom-end</Button>
              </MuiTooltip>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
    </Grid>
  );
}
export default Tooltip;
