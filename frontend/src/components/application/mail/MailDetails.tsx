'use client';

import { Button,
  CardContent,
  Checkbox,
  Collapse,
  Grid,
  IconButton,
  Menu,
  MenuItem,
  Stack,
  Typography,
  useMediaQuery,
 } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { format } from 'date-fns';
import { useState } from 'react';

// material-ui

// third-party
import ReactMarkdown from 'react-markdown';
import gfm from 'remark-gfm';
// ReactQuill temporarily disabled due to React 18 compatibility issues

// project imports
import useConfig from 'hooks/useConfig';
import { gridSpacing } from 'constants/index';
import AttachmentCard from 'ui-component/cards/AttachmentCard';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';
import Avatar from 'ui-component/extended/Avatar';

// assets
import AttachmentTwoToneIcon from '@mui/icons-material/AttachmentTwoTone';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import NewReleasesTwoToneIcon from '@mui/icons-material/NewReleasesTwoTone';
import StarBorderTwoToneIcon from '@mui/icons-material/StarBorderTwoTone';
import StarTwoToneIcon from '@mui/icons-material/StarTwoTone';
import LabelTwoToneIcon from '@mui/icons-material/LabelTwoTone';
import KeyboardArrowLeftTwoToneIcon from '@mui/icons-material/KeyboardArrowLeftTwoTone';
import MoreHorizTwoToneIcon from '@mui/icons-material/MoreHorizTwoTone';
import ReplyTwoToneIcon from '@mui/icons-material/ReplyTwoTone';
import ForwardTwoToneIcon from '@mui/icons-material/ForwardTwoTone';

const avatarImage = '/assets/images/users';

// types
import { MailDetailsProps } from 'types/mail';

// ==============================|| MAIL DETAILS ||============================== //

const MailDetails = ({
  handleUserDetails,
  data,
  handleStarredChange,
  handleImportantChange,
}: MailDetailsProps) => {
  const theme = useTheme();
  const { fontFamily } = useConfig();
  const matchDownSM = useMediaQuery(theme.breakpoints.down('md'));

  const [anchorEl, setAnchorEl] = useState<Element | (() => Element) | null | undefined>(null);
  const handleClickSort = (event: React.MouseEvent<HTMLButtonElement> | undefined) => {
    setAnchorEl(event?.currentTarget);
  };
  const handleCloseSort = () => {
    setAnchorEl(null);
  };

  const [openQuill, setOpenQuill] = useState(false);
  const handleChangeQuill = () => {
    setOpenQuill(true);
  };

  return (
    <MainCard
      sx={{ bgcolor: theme.palette.mode === 'dark' ? 'dark.main' : 'grey.50' }}
      content={false}
    >
      <CardContent>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12 }}>
            <Grid
              container
              alignItems="center"
              justifyContent="space-between"
              spacing={matchDownSM ? 1 : 0}
            >
              <Grid>
                <Stack direction="row" alignItems="center" spacing={matchDownSM ? 1 : 2}>
                  <IconButton
                    onClick={e => handleUserDetails(e, null)}
                    size="small"
                    aria-label="prev"
                  >
                    <KeyboardArrowLeftTwoToneIcon />
                  </IconButton>
                  <Avatar
                    alt={data?.profile.name || ''}
                    src={data?.profile?.avatar ? 
                      `${avatarImage}/${data.profile.avatar}` : ''
                    }
                    size={matchDownSM ? 'xs' : 'sm'}
                  />
                  <Grid container alignItems="center">
                    <Grid size={{ xs: 12 }}>
                      <Stack
                        direction={matchDownSM ? 'column' : 'row'}
                        alignItems={matchDownSM ? 'flex-start' : 'center'}
                        spacing={matchDownSM ? 0 : 1}
                      >
                        <Typography variant={matchDownSM ? 'h5' : 'h4'}>
                          {data?.profile.name}
                        </Typography>
                        <Typography
                          sx={{ display: { xs: 'block', sm: 'none' } }}
                          variant="subtitle2"
                        >
                          From: &lt;{data?.profile.to}&gt;
                        </Typography>
                      </Stack>
                    </Grid>
                    <Grid sx={{ display: { xs: 'none', sm: 'block' } }}>
                      <Typography variant="subtitle2">From: &lt;{data?.profile.to}&gt;</Typography>
                    </Grid>
                  </Grid>
                </Stack>
              </Grid>
              <Grid>
                <Typography variant="subtitle2">
                  {data?.time ? format(new Date(data.time), 'd MMM') : ''}
                </Typography>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
      </CardContent>
      <CardContent sx={{ pt: 0 }}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12 }}>
            <Grid container spacing={gridSpacing}>
              <Grid size={{ xs: 12 }}>
                <Grid container alignItems="center" spacing={0}>
                  <Grid>
                    <Typography variant={matchDownSM ? 'h4' : 'h3'}>{data?.subject}</Typography>
                  </Grid>
                  <Grid size="grow" sx={{ minWidth: 0 }} />
                  <Grid>
                    <Checkbox
                      checked={data?.starred}
                      icon={<StarBorderTwoToneIcon />}
                      checkedIcon={<StarTwoToneIcon />}
                      sx={{ '&.Mui-checked': { color: theme.palette.warning.dark } }}
                      onChange={event => handleStarredChange(event, data)}
                    />
                  </Grid>
                  <Grid>
                    <Checkbox
                      checked={data?.important}
                      icon={<LabelTwoToneIcon />}
                      checkedIcon={<LabelTwoToneIcon />}
                      sx={{ '&.Mui-checked': { color: theme.palette.secondary.main } }}
                      onChange={event => handleImportantChange(event, data)}
                    />
                  </Grid>
                  <Grid>
                    <Checkbox
                      defaultChecked={false}
                      icon={<NewReleasesTwoToneIcon />}
                      checkedIcon={<NewReleasesTwoToneIcon />}
                      sx={{ '&.Mui-checked': { color: theme.palette.error.main } }}
                    />
                  </Grid>
                  <Grid>
                    <IconButton onClick={handleClickSort} size="large" aria-label="more options">
                      <MoreHorizTwoToneIcon />
                    </IconButton>
                    <Menu
                      id="simple-menu"
                      anchorEl={anchorEl}
                      keepMounted
                      open={Boolean(anchorEl)}
                      onClose={handleCloseSort}
                      anchorOrigin={{
                        vertical: 'bottom',
                        horizontal: 'right',
                      }}
                      transformOrigin={{
                        vertical: 'top',
                        horizontal: 'right',
                      }}
                    >
                      <MenuItem onClick={handleCloseSort}>Name</MenuItem>
                      <MenuItem onClick={handleCloseSort}>Date</MenuItem>
                      <MenuItem onClick={handleCloseSort}>Ratting</MenuItem>
                      <MenuItem onClick={handleCloseSort}>Unread</MenuItem>
                    </Menu>
                  </Grid>
                </Grid>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Grid container spacing={gridSpacing}>
                  <Grid size={{ xs: 12 }}>
                    <Typography variant="body2">Dear {data?.profile.name},</Typography>
                  </Grid>
                  <Grid size={{ xs: 12 }}
                    sx={{
                      '& > p': {
                        marginBottom: 0
                      }
                    }}
                  >
                    <ReactMarkdown remarkPlugins={[gfm]}>{data?.message ?? ''}</ReactMarkdown>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <Typography variant="body2">Kind Regards,</Typography>
                    <Typography variant="body2">{data?.sender.name}</Typography>
                  </Grid>
                </Grid>
              </Grid>
              {data?.attachments && (
                <Grid size={{ xs: 12 }}>
                  <Grid container spacing={gridSpacing}>
                  <Grid size={{ xs: 12 }}>
                    <Stack direction="row" alignItems="center" spacing={1}>
                      <AttachmentTwoToneIcon fontSize="small" />
                      <Typography variant="h5">{data?.attachments?.length ?? 0} </Typography>
                      <Typography variant="h5">Attachments</Typography>
                    </Stack>
                  </Grid>
                    {data?.attachments.map((item, index) => (
                      <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={index}>
                        <AttachmentCard image={item.image} title={item.title} />
                      </Grid>
                    ))}
                  </Grid>
                </Grid>
              )}
              <Grid size={{ xs: 12 }}>
                <Grid container spacing={1} alignItems="center">
                  <Grid>
                    <Button
                      variant="outlined"
                      startIcon={<ReplyTwoToneIcon />}
                      onClick={handleChangeQuill}
                    >
                      Reply
                    </Button>
                  </Grid>
                  <Grid>
                    <Button
                      variant="outlined"
                      startIcon={<ForwardTwoToneIcon />}
                      onClick={handleChangeQuill}
                    >
                      Forward
                    </Button>
                  </Grid>
                </Grid>
              </Grid>
              <Collapse in={openQuill} sx={{ width: '100%' }}>
                {openQuill && (
                  <Grid size={{ xs: 12 }} sx={{ p: 3 }}>
                    <SubCard
                      sx={{
                        bgcolor:
                          theme.palette.mode === 'dark'
                            ? 'dark.dark'
                            : (theme.palette.primary[200] ?? theme.palette.primary.main) + 40,
                        '& .quill': {
                          bgcolor: theme.palette.mode === 'dark' ? 'dark.main' : 'grey.50',
                          borderRadius: '12px',
                          '& .ql-toolbar': {
                            bgcolor: theme.palette.mode === 'dark' ? 'dark.light' : 'grey.100',
                            borderColor:
                              theme.palette.mode === 'dark'
                                ? theme.palette.dark.light + 20
                                : theme.palette.primary.light,
                            borderTopLeftRadius: '12px',
                            borderTopRightRadius: '12px',
                          },
                          '& .ql-container': {
                            fontFamily,
                            fontSize: '0.875rem',
                            borderColor:
                              theme.palette.mode === 'dark'
                                ? `${theme.palette.dark.light + 20} !important`
                                : theme.palette.primary.light,
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
                        placeholder="Compose your reply here..."
                      />
                      <Grid container spacing={1} alignItems="center" sx={{ mt: 2 }}>
                        <Grid>
                          <IconButton size="large" aria-label="upload file">
                            <UploadFileIcon />
                          </IconButton>
                        </Grid>
                        <Grid>
                          <IconButton size="large" aria-label="any attachment file">
                            <AttachmentTwoToneIcon />
                          </IconButton>
                        </Grid>
                        <Grid sx={{ flexGrow: 1 }} />
                        <Grid>
                          <Button
                            onClick={() => setOpenQuill(false)}
                            sx={{ color: theme.palette.grey[900] }}
                          >
                            Cancel
                          </Button>
                        </Grid>
                        <Grid>
                          <Button variant="contained">Reply</Button>
                        </Grid>
                      </Grid>
                    </SubCard>
                  </Grid>
                )}
              </Collapse>
            </Grid>
          </Grid>
        </Grid>
      </CardContent>
    </MainCard>
  );
};

export default MailDetails;
