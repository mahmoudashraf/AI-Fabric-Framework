'use client';

// material-ui

// project imports
import { useUser } from 'contexts/UserContext';
import { useNotifications } from 'contexts/NotificationContext';
import { useAsyncOperation } from '@/hooks/enterprise';
import { useQuery } from '@tanstack/react-query';
import { aiProfileApi } from '@/services/ai-profile-api';
// import { useUserQuery } from 'hooks/useUserQuery'; // TODO: Implement this hook

// Using Context API

// types
import { PostDataType, Reply } from 'types/user-profile';

// assets & UI components
import AttachmentTwoToneIcon from '@mui/icons-material/AttachmentTwoTone';
import FacebookIcon from '@mui/icons-material/Facebook';
import InstagramIcon from '@mui/icons-material/Instagram';
import LayersTwoToneIcon from '@mui/icons-material/LayersTwoTone';
import LinkedInIcon from '@mui/icons-material/LinkedIn';
import NavigateNextRoundedIcon from '@mui/icons-material/NavigateNextRounded';
import PeopleAltTwoToneIcon from '@mui/icons-material/PeopleAltTwoTone';
import PublicTwoToneIcon from '@mui/icons-material/PublicTwoTone';
import RecentActorsTwoToneIcon from '@mui/icons-material/RecentActorsTwoTone';
import WorkOutlineTwoToneIcon from '@mui/icons-material/WorkOutlineTwoTone';
import { Box, Button, Divider, Grid, IconButton, Link, TextField, Typography, Chip, CircularProgress, Alert  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import React from 'react';
import MainCard from 'ui-component/cards/MainCard';
import Posts from 'ui-component/cards/Post';
import AnimateButton from 'ui-component/extended/AnimateButton';
// Grid spacing constant (moved from store)
const gridSpacing = 3;

// ==============================|| SOCIAL PROFILE - POST ||============================== //

const Profile = () => {
  const theme = useTheme();
  
  // Use Context API for user and notifications
  const userContext = useUser();
  const notificationContext = useNotifications();
  
  // Fetch the latest published AI profile
  const { data: aiProfile, isLoading: aiProfileLoading } = useQuery({
    queryKey: ['aiProfile', 'latest'],
    queryFn: async () => {
      try {
        const profile = await aiProfileApi.getLatestProfile();
        if (profile && profile.status === 'COMPLETE') {
          return aiProfileApi.parseAiAttributes(profile.aiAttributes);
        }
        return null;
      } catch (error) {
        console.log('No published AI profile found:', error);
        return null;
      }
    },
    retry: false,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
  
  // Get posts data from user context
  const { data: _posts, isLoading: postsLoading, refetch: _refetchPosts } = { 
    data: userContext.posts, 
    isLoading: false, 
    refetch: () => Promise.resolve() 
  };

  // Enterprise Pattern: Edit comment with retry
  const { execute: editCommentOp } = useAsyncOperation(
    async (id: string, commentId: string) => {
      await userContext.editComment(id, commentId);
      notificationContext.showNotification({
        open: true,
        message: 'Comment updated successfully',
        variant: 'alert',
        alert: { color: 'success', variant: 'filled' },
        close: true,
      });
      return true;
    },
    {
      retryCount: 1,
      retryDelay: 300,
      onError: () => {
        notificationContext.showNotification({
          open: true,
          message: 'Failed to update comment',
          variant: 'alert',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      },
    }
  );

  const editPost = async (id: string, commentId: string) => {
    await editCommentOp(id, commentId);
  };

  // Enterprise Pattern: Add comment with retry
  const { execute: addCommentOp } = useAsyncOperation(
    async (id: string, comment: Reply) => {
      await userContext.addComment(id, comment);
      notificationContext.showNotification({
        open: true,
        message: 'Comment added successfully',
        variant: 'alert',
        alert: { color: 'success', variant: 'filled' },
        close: true,
      });
      return true;
    },
    {
      retryCount: 1,
      retryDelay: 300,
      onError: () => {
        notificationContext.showNotification({
          open: true,
          message: 'Failed to add comment',
          variant: 'alert',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      },
    }
  );

  const commentAdd = async (id: string, comment: Reply) => {
    await addCommentOp(id, comment);
  };

  // Enterprise Pattern: Add reply with retry
  const { execute: addReplyOp } = useAsyncOperation(
    async (postId: string, commentId: string, reply: Reply) => {
      await userContext.addReply(postId, commentId, reply);
      notificationContext.showNotification({
        open: true,
        message: 'Reply added successfully',
        variant: 'alert',
        alert: { color: 'success', variant: 'filled' },
        close: true,
      });
      return true;
    },
    {
      retryCount: 1,
      retryDelay: 300,
      onError: () => {
        notificationContext.showNotification({
          open: true,
          message: 'Failed to add reply',
          variant: 'alert',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      },
    }
  );

  const replyAdd = async (postId: string, commentId: string, reply: Reply) => {
    await addReplyOp(postId, commentId, reply);
  };

  // Enterprise Pattern: Like post with retry
  const { execute: likePostOp } = useAsyncOperation(
    async (postId: string) => {
      await userContext.likePost(postId);
      return true;
    },
    {
      retryCount: 1,
      retryDelay: 300,
      onError: () => {
        notificationContext.showNotification({
          open: true,
          message: 'Failed to like post',
          variant: 'alert',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      },
    }
  );

  const handlePostLikes = async (postId: string) => {
    await likePostOp(postId);
  };

  // Enterprise Pattern: Like comment with retry
  const { execute: likeCommentOp } = useAsyncOperation(
    async (postId: string, commentId: string) => {
      await userContext.likeComment(postId, commentId);
      return true;
    },
    {
      retryCount: 1,
      retryDelay: 300,
      onError: () => {
        notificationContext.showNotification({
          open: true,
          message: 'Failed to like comment',
          variant: 'alert',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      },
    }
  );

  const handleCommentLikes = async (postId: string, commentId: string) => {
    await likeCommentOp(postId, commentId);
  };

  // Enterprise Pattern: Like reply with retry
  const { execute: likeReplyOp } = useAsyncOperation(
    async (postId: string, commentId: string, replayId: string) => {
      await userContext.likeReply(postId, commentId, replayId);
      return true;
    },
    {
      retryCount: 1,
      retryDelay: 300,
      onError: () => {
        notificationContext.showNotification({
          open: true,
          message: 'Failed to like reply',
          variant: 'alert',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      },
    }
  );

  const handleReplayLikes = async (postId: string, commentId: string, replayId: string) => {
    await likeReplyOp(postId, commentId, replayId);
  };

  // Get posts data from user context
  const postsData = userContext.posts;

  const sideAvatarSX = {
    borderRadius: '8px',
    width: 48,
    height: 48,
    fontSize: '1.5rem',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    border: theme.palette.mode === 'dark' ? '1px solid' : 'none',
    '&>svg': {
      width: 24,
      height: 24,
    },
  };

  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12, md: 4 }}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12 }}>
            <MainCard>
              <Grid container alignItems="center" spacing={gridSpacing}>
                <Grid>
                  <Box
                    sx={{
                      ...sideAvatarSX,
                      bgcolor:
                        theme.palette.mode === 'dark'
                          ? theme.palette.primary.main + 20
                          : 'primary.light',
                      border: theme.palette.mode === 'dark' ? '1px solid' : 'none',
                      borderColor: 'primary.main',
                      color: 'primary.dark',
                    }}
                  >
                    <PeopleAltTwoToneIcon />
                  </Box>
                </Grid>
                <Grid size="grow" sx={{ minWidth: 0 }}>
                  <Typography variant="h3" color="primary" component="div" sx={{ mb: 0.625 }}>
                    239k
                  </Typography>
                  <Typography variant="body2">Friends</Typography>
                </Grid>
                <Grid>
                  <IconButton size="large" aria-label="navigation icon">
                    <NavigateNextRoundedIcon />
                  </IconButton>
                </Grid>
              </Grid>
              <Divider sx={{ margin: '16px 0' }} />
              <Grid container alignItems="center" spacing={gridSpacing}>
                <Grid>
                  <Box
                    sx={{
                      ...sideAvatarSX,
                      bgcolor:
                        theme.palette.mode === 'dark'
                          ? theme.palette.secondary.main + 20
                          : 'secondary.light',
                      borderColor: 'secondary.main',
                      color: 'secondary.dark',
                    }}
                  >
                    <RecentActorsTwoToneIcon />
                  </Box>
                </Grid>
                <Grid size="grow" sx={{ minWidth: 0 }}>
                  <Typography
                    variant="h3"
                    sx={{
                      mb: 0.625,
                      color:
                        theme.palette.mode === 'dark'
                          ? theme.palette.text.secondary
                          : 'secondary.main',
                    }}
                  >
                    234k
                  </Typography>
                  <Typography variant="body2">Followers</Typography>
                </Grid>
                <Grid>
                  <IconButton size="large" aria-label="navigation icon">
                    <NavigateNextRoundedIcon />
                  </IconButton>
                </Grid>
              </Grid>
            </MainCard>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <MainCard>
              {aiProfileLoading ? (
                <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
                  <CircularProgress size={30} />
                </Box>
              ) : aiProfile ? (
                <>
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12 }}>
                      <Typography variant="h4">About</Typography>
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <Box sx={{ mb: 1 }}>
                        <Typography variant="subtitle1" fontWeight="bold" color="primary">
                          {aiProfile.jobTitle}
                        </Typography>
                        <Typography variant="body2" color="textSecondary">
                          {aiProfile.experience} years of experience
                        </Typography>
                      </Box>
                      <Typography variant="body2">
                        {aiProfile.profileSummary}
                      </Typography>
                    </Grid>
                  </Grid>
                  
                  <Divider sx={{ margin: '16px 0' }} />
                  
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12 }}>
                      <Typography variant="h6" gutterBottom>
                        Skills
                      </Typography>
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                        {aiProfile.skills.slice(0, 6).map((skill, index) => (
                          <Chip 
                            key={index} 
                            label={skill} 
                            size="small" 
                            color="primary" 
                            variant="outlined" 
                          />
                        ))}
                        {aiProfile.skills.length > 6 && (
                          <Chip 
                            label={`+${aiProfile.skills.length - 6} more`} 
                            size="small" 
                            variant="outlined" 
                          />
                        )}
                      </Box>
                    </Grid>
                  </Grid>
                  
                  <Divider sx={{ margin: '16px 0' }} />
                  
                  <Grid
                    container
                    spacing={2}
                    sx={{
                      '& >div': {
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                        display: 'block',
                        width: '100%',
                      },
                      '& a': {
                        color: theme.palette.grey[700],
                        '& svg': {
                          mr: 1,
                          verticalAlign: 'bottom',
                        },
                        '&:hover': {
                          color: theme.palette.primary.main,
                          textDecoration: 'none',
                        },
                      },
                    }}
                  >
                    <Grid size={{ xs: 12 }}>
                      <Link href="https://codedthemes.com/" target="_blank" underline="hover">
                        <PublicTwoToneIcon color="secondary" /> https://codedthemes.com/
                      </Link>
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <Link
                        href="https://www.instagram.com/codedthemes"
                        target="_blank"
                        underline="hover"
                      >
                        <InstagramIcon sx={{ color: theme.palette.orange.dark }} />{' '}
                        https://www.instagram.com/codedthemes
                      </Link>
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <Link
                        href="https://www.facebook.com/codedthemes"
                        target="_blank"
                        underline="hover"
                      >
                        <FacebookIcon color="primary" /> https://www.facebook.com/codedthemes
                      </Link>
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <Link
                        href="https://in.linkedin.com/company/codedthemes"
                        target="_blank"
                        underline="hover"
                      >
                        <LinkedInIcon sx={{ color: theme.palette.grey[900] }} />{' '}
                        https://in.linkedin.com/company/codedthemes
                      </Link>
                    </Grid>
                  </Grid>
                </>
              ) : (
                <>
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12 }}>
                      <Typography variant="h4">About</Typography>
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <Alert severity="info" sx={{ mb: 2 }}>
                        No AI profile generated yet. Visit the "Generate with AI" tab to create your professional profile.
                      </Alert>
                      <Typography variant="body2">
                        It is a long established fact that a reader will be distracted by the readable
                        content of a page when looking at its layout.
                      </Typography>
                    </Grid>
                  </Grid>
                  <Divider sx={{ margin: '16px 0' }} />
                  <Grid
                    container
                    spacing={2}
                    sx={{
                      '& >div': {
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                        display: 'block',
                        width: '100%',
                      },
                      '& a': {
                        color: theme.palette.grey[700],
                        '& svg': {
                          mr: 1,
                          verticalAlign: 'bottom',
                        },
                        '&:hover': {
                          color: theme.palette.primary.main,
                          textDecoration: 'none',
                        },
                      },
                    }}
                  >
                    <Grid size={{ xs: 12 }}>
                      <Link href="https://codedthemes.com/" target="_blank" underline="hover">
                        <PublicTwoToneIcon color="secondary" /> https://codedthemes.com/
                      </Link>
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <Link
                        href="https://www.instagram.com/codedthemes"
                        target="_blank"
                        underline="hover"
                      >
                        <InstagramIcon sx={{ color: theme.palette.orange.dark }} />{' '}
                        https://www.instagram.com/codedthemes
                      </Link>
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <Link
                        href="https://www.facebook.com/codedthemes"
                        target="_blank"
                        underline="hover"
                      >
                        <FacebookIcon color="primary" /> https://www.facebook.com/codedthemes
                      </Link>
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <Link
                        href="https://in.linkedin.com/company/codedthemes"
                        target="_blank"
                        underline="hover"
                      >
                        <LinkedInIcon sx={{ color: theme.palette.grey[900] }} />{' '}
                        https://in.linkedin.com/company/codedthemes
                      </Link>
                    </Grid>
                  </Grid>
                </>
              )}
            </MainCard>
          </Grid>
          
          {/* Work Experience Section (Only shown if AI profile exists) */}
          {aiProfile && aiProfile.companies.length > 0 && (
            <Grid size={{ xs: 12 }}>
              <MainCard>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <WorkOutlineTwoToneIcon color="primary" />
                      <Typography variant="h4">Work Experience</Typography>
                    </Box>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    {aiProfile.companies.slice(0, 3).map((company, index) => (
                      <Box
                        key={index}
                        sx={{
                          mb: 2,
                          pb: 2,
                          borderBottom: index < Math.min(aiProfile.companies.length, 3) - 1 ? 1 : 0,
                          borderColor: 'divider',
                        }}
                      >
                        <Typography variant="subtitle1" fontWeight="bold">
                          {company.position}
                        </Typography>
                        <Typography variant="body2" color="textSecondary">
                          {company.name}
                        </Typography>
                        <Typography variant="caption" color="textSecondary">
                          {company.duration}
                        </Typography>
                      </Box>
                    ))}
                    {aiProfile.companies.length > 3 && (
                      <Typography variant="caption" color="textSecondary">
                        +{aiProfile.companies.length - 3} more positions
                      </Typography>
                    )}
                  </Grid>
                </Grid>
              </MainCard>
            </Grid>
          )}
        </Grid>
      </Grid>
      <Grid size={{ xs: 12, md: 8 }}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12 }}>
            <MainCard>
              <Grid container spacing={gridSpacing}>
                <Grid size={{ xs: 12 }}>
                  <TextField
                    id="outlined-textarea"
                    placeholder="What's on your mind, Larry?"
                    rows={4}
                    fullWidth
                    multiline
                  />
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <Grid container justifyContent="space-between" spacing={gridSpacing}>
                    <Grid>
                      <Button
                        variant="text"
                        color="secondary"
                        startIcon={<AttachmentTwoToneIcon />}
                      >
                        Gallery
                      </Button>
                    </Grid>
                    <Grid>
                      <AnimateButton>
                        <Button
                          variant="contained"
                          color="secondary"
                          startIcon={<LayersTwoToneIcon />}
                        >
                          Post
                        </Button>
                      </AnimateButton>
                    </Grid>
                  </Grid>
                </Grid>
              </Grid>
            </MainCard>
          </Grid>
          {postsData && postsData.length > 0 ? (
            postsData.map((post: PostDataType, _index: number) => (
              <Grid key={post.id} size={{ xs: 12 }}>
                <Posts
                  key={post.id}
                  post={post}
                  editPost={editPost}
                  renderPost={() => userContext.getPosts()}
                  setPosts={() => {}} // Not needed with Context API
                  commentAdd={commentAdd}
                  replyAdd={replyAdd}
                  handlePostLikes={handlePostLikes}
                  handleCommentLikes={handleCommentLikes}
                  handleReplayLikes={handleReplayLikes}
                />
              </Grid>
            ))
          ) : postsLoading ? (
            <Grid size={{ xs: 12 }}>
              <Typography>Loading posts...</Typography>
            </Grid>
          ) : (
            <Grid size={{ xs: 12 }}>
              <Typography>No posts available</Typography>
            </Grid>
          )}
        </Grid>
      </Grid>
    </Grid>
  );
};

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(Profile);
