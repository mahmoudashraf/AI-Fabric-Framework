'use client';

//material

// third-party

// project import

//assets
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CloseOutlinedIcon from '@mui/icons-material/CloseOutlined';
import KeyboardArrowDownOutlinedIcon from '@mui/icons-material/KeyboardArrowDownOutlined';
import {
  Box,
  Button,
  Card,
  Typography,
  CardActions,
  IconButton,
  Collapse,
  Paper,
} from '@mui/material';
import { styled } from '@mui/material/styles';
import {
  enqueueSnackbar,
  useSnackbar,
  SnackbarContent,
  SnackbarKey,
  SnackbarMessage,
} from 'notistack';
import { useState, forwardRef, useCallback } from 'react';

import SubCard from 'ui-component/cards/SubCard';

const SnackbarBox = styled(SnackbarContent)(({ theme: _theme }) => ({
  '@media (min-width:600px)': {
    minWidth: '344px !important',
  },
}));

const CustomNotistack = forwardRef((props: any, ref: any) => {
  const { closeSnackbar } = useSnackbar();
  const [expanded, setExpanded] = useState<boolean>(false);

  const handleExpandClick = useCallback(() => {
    setExpanded((prevState: boolean) => !prevState);
  }, []);

  const handleDismiss = useCallback(() => {
    closeSnackbar(props.id);
  }, [props.id, closeSnackbar]);

  return (
    <SnackbarBox ref={ref}>
      <Card sx={{ bgcolor: 'warning.light', width: '100%' }}>
        <CardActions sx={{ padding: '8px 8px 8px 16px', justifyContent: 'space-between' }}>
          <Typography variant="subtitle2">{props.message}</Typography>
          <Box sx={{ marginLeft: 'auto' }}>
            <IconButton
              aria-label="Show more"
              sx={{
                p: 1,
                transition: 'all .2s',
                transform: expanded ? 'rotate(180deg)' : 'rotate(0deg)',
              }}
              onClick={handleExpandClick}
            >
              <KeyboardArrowDownOutlinedIcon />
            </IconButton>
            <IconButton sx={{ p: 1, transition: 'all .2s' }} onClick={handleDismiss}>
              <CloseOutlinedIcon />
            </IconButton>
          </Box>
        </CardActions>
        <Collapse in={expanded} timeout="auto" unmountOnExit>
          <Paper
            sx={{
              padding: 2,
              borderTopLeftRadius: 0,
              borderTopRightRadius: 0,
              bgcolor: 'warning.lighter',
            }}
          >
            <Typography gutterBottom>PDF ready</Typography>
            <Button
              size="small"
              startIcon={<CheckCircleIcon sx={{ fontSize: 16 }} />}
              sx={{ '&:hover': { bgcolor: 'transparent' } }}
            >
              Download now
            </Button>
          </Paper>
        </Collapse>
      </Card>
    </SnackbarBox>
  );
});

// ==============================|| NOTISTACK - CUSTOM STYLE ||============================== //

export default function CustomComponent() {
  return (
    <SubCard title="Custom Component">
      <Button
        variant="outlined"
        fullWidth
        sx={{ marginBlockStart: 2 }}
        onClick={() => {
          enqueueSnackbar("You're report is ready", {
            anchorOrigin: {
              vertical: 'bottom',
              horizontal: 'right',
            },
            content: (key: SnackbarKey, message: SnackbarMessage) => (
              <CustomNotistack id={key} message={message} />
            ),
          });
        }}
      >
        Show snackbar
      </Button>
    </SubCard>
  );
}
