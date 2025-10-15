'use client';

// material-ui
import { Box, Grid, useMediaQuery  } from '@mui/material';
import { styled, useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';

// project imports
import MailDetails from 'components/application/mail/MailDetails';
import MailDrawer from 'components/application/mail/MailDrawer';
import MailList from 'components/application/mail/MailList';
import { useMail } from 'contexts/MailContext';
import { useNotifications } from 'contexts/NotificationContext';
import { useAsyncOperation } from '@/hooks/enterprise';

// types
import { KeyedObject } from 'types';
import { MailProps, MailDetailsProps, MailBoxCount } from 'types/mail';
import Loader from 'ui-component/Loader';

const gridSpacing = 3;
const drawerWidth = 320;

// drawer content element
const Main = styled('main', { shouldForwardProp: prop => prop !== '$open' })<{ $open: boolean }>(
  ({ theme, $open }) => ({
    width: 'calc(100% - 320px)',
    flexGrow: 1,
    paddingLeft: $open ? theme.spacing(3) : 0,
    transition: theme.transitions.create('margin', {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.shorter,
    }),
    marginLeft: `-${drawerWidth}px`,
    [theme.breakpoints.down('xl')]: {
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
  })
);

// ==============================|| MAIL MAIN PAGE ||============================== //

const MailPage = () => {
  const theme = useTheme();
  const matchDownSM = useMediaQuery(theme.breakpoints.down('xl'));
  const [loading, setLoading] = useState<boolean>(true);

  // Using Context API
  const mailContext = useMail();
  const notificationContext = useNotifications();

  const [emailDetails, setEmailDetailsValue] = useState(false);
  const [selectedMail, setSelectedMail] = useState<MailProps | null>(null);
  
  // Enterprise Pattern: Mark email as read with retry
  const { execute: markAsRead } = useAsyncOperation(
    async (mailId: string) => {
      await mailContext.setRead(mailId);
      await mailContext.getMails();
      return true;
    },
    {
      retryCount: 1,
      retryDelay: 300,
      onError: () => {
        notificationContext.showNotification({
          message: 'Failed to mark email as read',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: false,
        });
      },
    }
  );

  const handleUserChange = async (data: MailProps | null) => {
    if (data) {
      await markAsRead(data.id);
    }
    setSelectedMail(data);
    setEmailDetailsValue(prev => !prev);
  };

  const [openMailSidebar, setOpenMailSidebar] = useState(true);
  const handleDrawerOpen = () => {
    setOpenMailSidebar(prevState => !prevState);
  };

  useEffect(() => {
    if (matchDownSM) {
      setOpenMailSidebar(false);
    } else {
      setOpenMailSidebar(true);
    }
  }, [matchDownSM]);

  const [data, setData] = useState<MailProps[]>([]);
  const [unreadCounts, setUnreadCounts] = useState<MailBoxCount>();
  
  // Use Context API directly
  const { mails, unreadCount } = mailContext.state;

  useEffect(() => {
    setData(mails || []);
    setUnreadCounts({ all: unreadCount } as unknown  as MailBoxCount );
  }, [mails, unreadCount]);

  // Enterprise Pattern: Load mails with retry
  const { execute: loadMails } = useAsyncOperation(
    async () => {
      await mailContext.getMails();
      setLoading(false);
      return true;
    },
    {
      retryCount: 2,
      retryDelay: 500,
      onError: () => {
        setLoading(false);
        notificationContext.showNotification({
          message: 'Failed to load emails',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: false,
        });
      },
    }
  );

  useEffect(() => {
    loadMails();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Enterprise Pattern: Filter mails with retry
  const { execute: filterMailsOp } = useAsyncOperation(
    async (filterString: string) => {
      await mailContext.filterMails(filterString);
      return true;
    },
    {
      retryCount: 1,
      retryDelay: 300,
      onError: () => {
        notificationContext.showNotification({
          message: 'Failed to filter emails',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: false,
        });
      },
    }
  );

  const [filter, setFilter] = useState('all');
  const handleFilter = async (string: string) => {
    setEmailDetailsValue(false);
    setFilter(string);
    await filterMailsOp(string);
  };

  // Enterprise Pattern: Mark as important with retry
  const { execute: markImportant } = useAsyncOperation(
    async (mailId: string) => {
      await mailContext.setImportant(mailId);
      await handleFilter(filter);
      return true;
    },
    {
      retryCount: 1,
      retryDelay: 300,
      onError: () => {
        notificationContext.showNotification({
          message: 'Failed to mark email as important',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: false,
        });
      },
    }
  );

  const handleImportantChange: MailDetailsProps['handleImportantChange'] = async (
    _event,
    dataImportant
  ) => {
    if (dataImportant) {
      await markImportant(dataImportant.id);
    }
  };

  // Enterprise Pattern: Mark as starred with retry
  const { execute: markStarred } = useAsyncOperation(
    async (mailId: string) => {
      await mailContext.setStarred(mailId);
      await handleFilter(filter);
      return true;
    },
    {
      retryCount: 1,
      retryDelay: 300,
      onError: () => {
        notificationContext.showNotification({
          message: 'Failed to mark email as starred',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: false,
        });
      },
    }
  );

  const handleStarredChange: MailDetailsProps['handleStarredChange'] = async (
    _event,
    dataStarred
  ) => {
    if (dataStarred) {
      await markStarred(dataStarred.id);
    }
  };

  // search email using name
  const [search, setSearch] = useState('');
  const handleSearch = (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const newString = event.target.value;
    setSearch(newString);

    if (newString) {
      const newRows = data.filter((row: KeyedObject) => {
        let matches = true;

        const properties = ['name'];
        let containsQuery = false;

        properties.forEach(property => {
          if (
            row.profile[property]
              .toString()
              .toLowerCase()
              .includes(newString.toString().toLowerCase())
          ) {
            containsQuery = true;
          }
        });

        if (!containsQuery) {
          matches = false;
        }
        return matches;
      });
      setData(newRows);
    } else {
      handleFilter(filter);
    }
  };

  if (loading) return <Loader />;

  return (
    <Box sx={{ display: 'flex' }}>
      <MailDrawer
        openMailSidebar={openMailSidebar}
        handleDrawerOpen={handleDrawerOpen}
        filter={filter}
        handleFilter={handleFilter}
        unreadCounts={unreadCounts}
      />
      <Main $open={openMailSidebar}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12 }}>
            {/* mail details & list */}
            {emailDetails ? (
              <MailDetails
                data={selectedMail}
                handleUserDetails={(_e, d) => handleUserChange(d)}
                handleImportantChange={handleImportantChange}
                handleStarredChange={handleStarredChange}
              />
            ) : (
              <MailList
                handleUserDetails={(_e, d) => handleUserChange(d)}
                handleDrawerOpen={handleDrawerOpen}
                handleImportantChange={handleImportantChange}
                handleStarredChange={handleStarredChange}
                data={data}
                search={search}
                handleSearch={handleSearch}
              />
            )}
          </Grid>
        </Grid>
      </Main>
    </Box>
  );
};

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(MailPage);
