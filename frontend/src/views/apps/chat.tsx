'use client';

import AttachmentTwoToneIcon from '@mui/icons-material/AttachmentTwoTone';
import CallTwoToneIcon from '@mui/icons-material/CallTwoTone';
import ErrorTwoToneIcon from '@mui/icons-material/ErrorTwoTone';
import HighlightOffTwoToneIcon from '@mui/icons-material/HighlightOffTwoTone';
import MenuRoundedIcon from '@mui/icons-material/MenuRounded';
import MoreHorizTwoToneIcon from '@mui/icons-material/MoreHorizTwoTone';
import SendTwoToneIcon from '@mui/icons-material/SendTwoTone';
import VideoCallTwoToneIcon from '@mui/icons-material/VideoCallTwoTone';
import { Box,
  Divider,
  Grid,
  IconButton,
  InputAdornment,
  OutlinedInput,
  Menu,
  MenuItem,
  Stack,
  Typography,
  useMediaQuery,
 } from '@mui/material';
import { useTheme, styled } from '@mui/material/styles';
import React, { useEffect, useState, useLayoutEffect, useRef } from 'react';

// material-ui

// third-party
import PerfectScrollbar from 'react-perfect-scrollbar';

// project imports
import ChartHistory from 'components/application/chat/ChartHistory';
import ChatDrawer from 'components/application/chat/ChatDrawer';
import UserDetails from 'components/application/chat/UserDetails';
import AvatarStatus from 'components/application/chat/AvatarStatus';
import MainCard from 'ui-component/cards/MainCard';
import Loader from 'ui-component/Loader';
import EmojiPicker from 'ui-component/third-party/EmojiPicker';

import Avatar from 'ui-component/extended/Avatar';
import { useChat } from 'contexts/ChatContext';
import { useNotifications } from 'contexts/NotificationContext';
// import { useChatQuery } from 'hooks/useChatQuery'; // TODO: Implement this hook

// Pure Context API usage
import { appDrawerWidth as drawerWidth, gridSpacing } from 'constants/index';

// assets

const avatarImage = '/assets/images/users';

// types
import { UserProfile } from 'types/user-profile';
import { History as HistoryProps } from 'types/chat';

// drawer content element
const Main = styled('main', {
  shouldForwardProp: (prop: string) => prop !== '$open',
})<{ $open: boolean }>(({ theme, $open }) => ({
    flexGrow: 1,
    paddingLeft: $open ? theme.spacing(3) : 0,
    transition: theme.transitions.create('margin', {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.shorter,
    }),
    marginLeft: `-${drawerWidth}px`,
    [theme.breakpoints.down('lg')]: {
      paddingLeft: 0,
      marginLeft: 0,
    },
    ...($open && {
      transition: theme.transitions.create('margin', {
        easing: theme.transitions.easing.easeOut,
        duration: theme.transitions.duration.shorter,
      }),
      marginLeft: 0,
    }),
}));

// ==============================|| APPLICATION CHAT ||============================== //

const ChatMainPage = () => {
  const theme = useTheme();
  const matchDownSM = useMediaQuery(theme.breakpoints.down('lg'));

  const [loading, setLoading] = useState<boolean>(true);

  // Using Context API
  const chatContext = useChat();
  // @ts-ignore
  const _unused_notificationContext = useNotifications();

  // Use Context API directly
  const scrollRef = useRef();

  useLayoutEffect(() => {
    if (scrollRef?.current) {
      // @ts-ignore
      scrollRef.current.scrollIntoView();
    }
  });

  // handle right sidebar dropdown menu
  const [anchorEl, setAnchorEl] = React.useState<Element | (() => Element) | null | undefined>(
    null
  );
  const handleClickSort = (event: React.MouseEvent<HTMLButtonElement> | undefined) => {
    setAnchorEl(event?.currentTarget);
  };

  const handleCloseSort = () => {
    setAnchorEl(null);
  };

  // set chat details page open when user is selected from sidebar
  const [emailDetails, setEmailDetails] = React.useState(false);
  const handleUserChange = (_event: React.SyntheticEvent) => {
    setEmailDetails(prev => !prev);
  };

  // toggle sidebar
  const [openChatDrawer, setOpenChatDrawer] = React.useState(true);
  const handleDrawerOpen = () => {
    setOpenChatDrawer(prevState => !prevState);
  };

  // close sidebar when widow size below 'md' breakpoint
  useEffect(() => {
    setOpenChatDrawer(!matchDownSM);
  }, [matchDownSM]);

  const [user, setUser] = useState<UserProfile>({});
  const [data, setData] = React.useState<HistoryProps[]>([]);

  useEffect(() => {
    setUser(chatContext.state.user);
  }, [chatContext.state.user]);

  useEffect(() => {
    setData(chatContext.state.chats);
  }, [chatContext.state.chats]);

  useEffect(() => {
    let isMounted = true;
    
    const loadUser = async () => {
      try {
        await chatContext.getUser(1);
        if (isMounted) {
          setLoading(false);
        }
      } catch (error) {
        console.warn('Failed to load user:', error);
        if (isMounted) {
          setLoading(false);
        }
      }
    };
    
    loadUser();
    
    return () => {
      isMounted = false;
    };
  }, []); // Empty dependency array - only run once on mount

  useEffect(() => {
    if (user.name) {
      const loadUserChats = async () => {
        try {
          await chatContext.getUserChats(user.name);
        } catch (error) {
          console.warn('Failed to load user chats:', error);
        }
      };
      
      loadUserChats();
    }
  }, [user.name]); // Only depend on user.name, not the entire chatContext

  // handle new message form
  const [message, setMessage] = useState('');
  const handleOnSend = () => {
    if (!user.name) return;
    
    const d = new Date();
    const newMessage = {
      from: 'User1',
      to: user.name,
      text: message,
      time: d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };
    setData(prevState => [...prevState, newMessage]);
    setMessage('');
    
    // Use Context API to insert chat
    try {
      chatContext.insertChat(newMessage);
    } catch (error) {
      console.warn('Failed to insert chat:', error);
    }
  };

  const handleEnter = (event: React.KeyboardEvent<HTMLDivElement> | undefined) => {
    if (event?.key !== 'Enter') {
      return;
    }
    handleOnSend();
  };

  if (loading) return <Loader />;

  return (
    <Box sx={{ display: 'flex' }}>
      <ChatDrawer
        openChatDrawer={openChatDrawer}
        handleDrawerOpen={handleDrawerOpen}
        setUser={setUser}
      />
      <Main $open={openChatDrawer}>
        <Grid container spacing={gridSpacing}>
          <Grid
            size={{ xs: 12 }}
            sx={{ display: emailDetails ? { xs: 'none', sm: 'flex' } : 'flex', minWidth: 0 }}
          >
            <MainCard
              sx={{
                bgcolor: theme.palette.mode === 'dark' ? 'dark.main' : 'grey.50',
              }}
            >
              <Grid container spacing={gridSpacing}>
                <Grid size={{ xs: 12 }}>
                  <Grid container alignItems="center" spacing={0.5}>
                    <Grid>
                      <IconButton
                        onClick={handleDrawerOpen}
                        size="large"
                        aria-label="click to menu collapse"
                      >
                        <MenuRoundedIcon />
                      </IconButton>
                    </Grid>
                    <Grid>
                      <Grid container spacing={2} alignItems="center" sx={{ flexWrap: 'nowrap' }}>
                        <Grid>
                          <Avatar
                            alt={user.name || 'User'}
                            {...(user.avatar && { src: `${avatarImage}/${user.avatar}` })}
                          />
                        </Grid>
                        <Grid size="grow" sx={{ minWidth: 0 }}>
                          <Grid container spacing={0} alignItems="center">
                            <Grid size={{ xs: 12 }}>
                              <Typography variant="h4" component="div">
                                {user.name}{' '}
                                {user.online_status && <AvatarStatus status={user.online_status} />}
                              </Typography>
                            </Grid>
                            <Grid size={{ xs: 12 }}>
                              <Typography variant="subtitle2">
                                Last seen {user.lastMessage}
                              </Typography>
                            </Grid>
                          </Grid>
                        </Grid>
                      </Grid>
                    </Grid>
                    <Grid size="grow" sx={{ minWidth: 0 }} />
                    <Grid>
                      <IconButton size="large" aria-label="phone call options">
                        <CallTwoToneIcon />
                      </IconButton>
                    </Grid>
                    <Grid>
                      <IconButton size="large" aria-label="video call options">
                        <VideoCallTwoToneIcon />
                      </IconButton>
                    </Grid>
                    <Grid>
                      <IconButton
                        onClick={handleUserChange}
                        size="large"
                        aria-label="user information options"
                      >
                        <ErrorTwoToneIcon />
                      </IconButton>
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
                  <Divider sx={{ mt: theme.spacing(2) }} />
                </Grid>
                <PerfectScrollbar
                  style={{
                    width: '100%',
                    height: 'calc(100vh - 440px)',
                    overflowX: 'hidden',
                    minHeight: 525,
                  }}
                >
                  <Box sx={{ py: 3, pl: 4, pr: 1 }}>
                    <ChartHistory user={user} data={data} />
                    {/* @ts-ignore */}
                    <span ref={scrollRef} />
                  </Box>
                </PerfectScrollbar>
                <Grid size={{ xs: 12 }}>
                  <Grid container spacing={1} alignItems="center">
                    <Grid size={{ xs: 12 }} sx={{ minWidth: 0 }}>
                      <OutlinedInput
                        fullWidth
                        value={message}
                        onKeyPress={handleEnter}
                        onChange={event => setMessage(event.target.value)}
                        placeholder="Type a message"
                        startAdornment={
                          <InputAdornment position="start">
                            <EmojiPicker value={message} setValue={setMessage} />
                          </InputAdornment>
                        }
                        endAdornment={
                          <Stack direction="row" spacing={1} alignItems="center">
                            <IconButton aria-label="attachment">
                              <AttachmentTwoToneIcon style={{ fontSize: '1.25rem' }} />
                            </IconButton>
                            <IconButton color="primary" aria-label="send" onClick={handleOnSend}>
                              <SendTwoToneIcon style={{ fontSize: '1.25rem' }} />
                            </IconButton>
                          </Stack>
                        }
                      />
                    </Grid>
                  </Grid>
                </Grid>
              </Grid>
            </MainCard>
          </Grid>
          {emailDetails && (
            <Grid sx={{ margin: { xs: '0 auto', md: 'initial' } }}>
              <Box sx={{ display: { xs: 'block', sm: 'none', textAlign: 'right' } }}>
                <IconButton
                  onClick={handleUserChange}
                  aria-label="highlight"
                  sx={{ mb: -5 }}
                  size="large"
                >
                  <HighlightOffTwoToneIcon />
                </IconButton>
              </Box>
              <UserDetails user={user} />
            </Grid>
          )}
        </Grid>
      </Main>
    </Box>
  );
};

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(ChatMainPage);
