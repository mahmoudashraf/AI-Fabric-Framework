'use client';

import { useState } from 'react';

// material-ui
import { Button,
  Collapse,
  Dialog,
  DialogContent,
  Grid,
  IconButton,
  Link as MuiLink,
  TextField,
  Typography,
 } from '@mui/material';

// third-party
// ReactQuill temporarily disabled due to React 18 compatibility issues

// project imports

// assets
import AddCircleOutlineTwoToneIcon from '@mui/icons-material/AddCircleOutlineTwoTone';
import AttachmentTwoToneIcon from '@mui/icons-material/AttachmentTwoTone';
import HighlightOffTwoToneIcon from '@mui/icons-material/HighlightOffTwoTone';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import { useTheme } from '@mui/material/styles';
import { IconArrowsDiagonal2 } from '@tabler/icons-react';
import Link from 'next/link';

import { gridSpacing } from 'constants/index';

// ==============================|| MAIL COMPOSE DIALOG ||============================== //

const ComposeDialog = () => {
  const theme = useTheme();

  const [open, setOpen] = useState(false);
  const handleClickOpen = () => {
    setOpen(true);
  };
  const handleCloseDialog = () => {
    setOpen(false);
  };

  const [ccBccValue, setCcBccValue] = useState<boolean>(false);
  const handleCcBccChange = (_event: React.MouseEvent<HTMLSpanElement> | undefined) => {
    setCcBccValue(prev => !prev);
  };

  let composePosition = {};

  const [position, setPosition] = useState(true);
  if (!position) {
    composePosition = {
      '& .MuiDialog-container': {
        justifyContent: 'flex-end',
        alignItems: 'flex-end',
        '& .MuiPaper-root': { mb: 0, borderRadius: '12px 12px 0px 0px', maxWidth: 595 },
      },
    };
  }

  return (
    <>
      <Button
        variant="contained"
        onClick={handleClickOpen}
        sx={{ width: '100%' }}
        size="large"
        startIcon={<AddCircleOutlineTwoToneIcon />}
      >
        Compose
      </Button>
      <Dialog
        open={open}
        keepMounted
        onClose={handleCloseDialog}
        sx={composePosition}
      >
        {open && (
          <DialogContent>
            <Grid container spacing={gridSpacing}>
              <Grid size={{ xs: 12 }}>
                <Grid container alignItems="center" spacing={0}>
                  <Grid size="grow" sx={{ minWidth: 0 }}>
                    <Typography component="div" align="left" variant="h4">
                      New Message
                    </Typography>
                  </Grid>
                  <Grid>
                    <IconButton onClick={() => setPosition(!position)} size="large">
                      <IconArrowsDiagonal2 />
                    </IconButton>
                  </Grid>
                  <Grid>
                    <IconButton onClick={handleCloseDialog} size="large">
                      <HighlightOffTwoToneIcon />
                    </IconButton>
                  </Grid>
                </Grid>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Grid container justifyContent="flex-end" spacing={0}>
                  <Grid>
                    <MuiLink
                      component={Link}
                      href="#"
                      color={theme.palette.mode === 'dark' ? 'primary' : 'secondary'}
                      onClick={handleCcBccChange}
                      underline="hover"
                    >
                      CC & BCC
                    </MuiLink>
                  </Grid>
                </Grid>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <TextField fullWidth label="To" />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <TextField fullWidth label="Subject" />
              </Grid>
              <Grid size={{ xs: 12 }} sx={{ display: ccBccValue ? 'block' : 'none' }}>
                <Collapse in={ccBccValue}>
                  {ccBccValue && (
                    <Grid container spacing={gridSpacing}>
                      <Grid size={{ xs: 12 }}>
                        <TextField fullWidth label="CC" />
                      </Grid>
                      <Grid size={{ xs: 12 }}>
                        <TextField fullWidth label="BCC" />
                      </Grid>
                    </Grid>
                  )}
                </Collapse>
              </Grid>

              {/* quill editor */}
              <Grid size={{ xs: 12 }}
                sx={{
                  '& .quill': {
                    bgcolor: theme.palette.mode === 'dark' ? 'dark.main' : 'grey.50',
                    borderRadius: '12px',
                    '& .ql-toolbar': {
                      bgcolor: theme.palette.mode === 'dark' ? 'dark.light' : 'grey.100',
                      borderColor:
                        theme.palette.mode === 'dark' ? theme.palette.dark.light + 20 : 'grey.400',
                      borderTopLeftRadius: '12px',
                      borderTopRightRadius: '12px',
                    },
                    '& .ql-container': {
                      borderColor:
                        theme.palette.mode === 'dark'
                          ? `${theme.palette.dark.light + 20} !important`
                          : `${theme.palette.grey[400]} !important`,
                      borderBottomLeftRadius: '12px',
                      borderBottomRightRadius: '12px',
                      '& .ql-editor': {
                        minHeight: 125,
                      },
                    },
                  },
                }}
              >
                <textarea
                  style={{
                    width: '100%',
                    minHeight: '200px',
                    padding: '10px',
                    border: '1px solid #ccc',
                    borderRadius: '4px',
                    fontFamily: 'inherit',
                    fontSize: '14px',
                    lineHeight: '1.5',
                    resize: 'vertical'
                  }}
                  placeholder="Compose your message here..."
                />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Grid container spacing={1} alignItems="center">
                  <Grid>
                    <IconButton size="large">
                      <UploadFileIcon />
                    </IconButton>
                  </Grid>
                  <Grid>
                    <IconButton size="large">
                      <AttachmentTwoToneIcon />
                    </IconButton>
                  </Grid>
                  <Grid sx={{ flexGrow: 1 }} />
                  <Grid>
                    <Button variant="contained">Reply</Button>
                  </Grid>
                </Grid>
              </Grid>
            </Grid>
          </DialogContent>
        )}
      </Dialog>
    </>
  );
};

export default ComposeDialog;
