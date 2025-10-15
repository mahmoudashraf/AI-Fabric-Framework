'use client';

import AddCircleOutlineOutlinedIcon from '@mui/icons-material/AddCircleOutlineOutlined';
import { Button, Divider, Grid, InputAdornment, OutlinedInput, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { IconSearch } from '@tabler/icons-react';
import { isEmpty } from 'lodash';
import { useEffect, useState, Fragment } from 'react';

// material-ui

// third-party

// project imports
import UserDetails from 'components/application/contact/UserDetails';
import UserEdit from 'components/application/contact/UserEdit';
import { useContact } from 'contexts/ContactContext';
import { useNotifications } from 'contexts/NotificationContext';
import { useAsyncOperation } from '@/hooks/enterprise';
import { UserProfile } from 'types/user-profile';
import ContactCard from 'ui-component/cards/ContactCard';
import MainCard from 'ui-component/cards/MainCard';

const gridSpacing = 3;

// types

// assets
const User1 = '/assets/images/users/avatar-1.png';

// ==============================|| CONTACT CARD ||============================== //

const ContactCardPage = () => {
  const theme = useTheme();
  
  // Using Context API
  const contactContext = useContact();
  const notificationContext = useNotifications();

  const [user, setUser] = useState<UserProfile>({});
  const [data, setData] = useState<Record<string, UserProfile[]>>({});

  // Use Context API directly
  const contacts = contactContext.state.contacts;
  const convertData = (userData: UserProfile[]) =>
    userData.reduce((acc: Record<string, UserProfile[]>, curr) => {
      const name = curr.name ?? '';
      const firstLetter = name.charAt(0).toUpperCase() || '#';
      if (Object.prototype.hasOwnProperty.call(acc, firstLetter)) {
        acc[firstLetter]!.push(curr);
      } else {
        acc[firstLetter] = [curr];
      }
      return acc;
    }, {} as Record<string, UserProfile[]>);

  useEffect(() => {
    setData(convertData(contacts));
    if (!isEmpty(user)) {
      const idx = contacts.findIndex((item: UserProfile) => item.id === user.id);
      if (idx > -1 && contacts[idx]) setUser(contacts[idx]!);
    }
  }, [contacts, user]);

  // Enterprise Pattern: Async operation with retry
  const { execute: loadContacts } = useAsyncOperation(
    async () => {
      await contactContext.getContacts();
      return true as const;
    },
    {
      retryCount: 2,
      retryDelay: 500,
      onError: () => {
        notificationContext.showNotification({
          message: 'Failed to load contacts',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: false,
        });
      },
    }
  );

  useEffect(() => {
    loadContacts();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Enterprise Pattern: Update contact with retry
  const { execute: updateContact } = useAsyncOperation(
    async (u: UserProfile) => {
      await contactContext.modifyContact(u);
      return true;
    },
    {
      retryCount: 1,
      retryDelay: 300,
      onError: () => {
        notificationContext.showNotification({
          message: 'Failed to update contact',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: false,
        });
      },
    }
  );

  const modifyUser = async (u: UserProfile) => {
    await updateContact(u);
  };

  // handle new user insert action
  const [userDetails, setUserDetails] = useState(false);
  const [userEdit, setUserEdit] = useState(false);
  const handleOnAdd = () => {
    setUser({
      name: '',
      company: '',
      role: '',
      work_email: '',
      personal_email: '',
      work_phone: '',
      personal_phone: '',
      location: 'USA',
      image: User1,
      status: 'I am online',
      lastMessage: '2h ago',
      birthdayText: '',
    });
    setUserDetails(false);
    setUserEdit(true);
  };

  return (
    <MainCard title="Contact Cards">
      <Grid container spacing={gridSpacing}>
        <Grid
          className="block"
          sx={{ minWidth: 0, display: userDetails || userEdit ? { xs: 'none', md: 'flex' } : 'flex' }}
        >
          <Grid container alignItems="center" spacing={gridSpacing}>
            <Grid size="grow" sx={{ minWidth: 0 }}>
              <OutlinedInput
                id="input-search-card-style1"
                placeholder="Search Contact"
                fullWidth
                startAdornment={
                  <InputAdornment position="start">
                    <IconSearch stroke={1.5} size="16px" />
                  </InputAdornment>
                }
              />
            </Grid>
            <Grid>
              <Button
                variant="contained"
                size="large"
                startIcon={<AddCircleOutlineOutlinedIcon />}
                onClick={handleOnAdd}
              >
                Add
              </Button>
            </Grid>

            {Object.keys(data).map((key: string, index) => (
              <Fragment key={index}>
                <Grid size={{ xs: 12 }}>
                  <Divider
                    sx={{ borderColor: theme.palette.primary.light, mt: 0.625, mb: 1.875 }}
                  />
                  <Typography variant="h4" color="primary" sx={{ fontSize: '1rem' }}>
                    {key.toUpperCase()}
                  </Typography>
                  <Divider
                    sx={{ borderColor: theme.palette.primary.light, mt: 1.875, mb: 0.625 }}
                  />
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <Grid container direction="row" spacing={gridSpacing}>
                    {data[key]?.map((userRow: UserProfile, i: number) => (
                      <Grid key={i} size={{ xs: 12, md: userEdit ? 12 : 6, lg: userEdit ? 6 : 4 }}>
                        <ContactCard
                          avatar={userRow.avatar ?? ''}
                          name={userRow.name ?? ''}
                          role={userRow.role ?? ''}
                          email={userRow.work_email ?? ''}
                          contact={userRow.phone ?? ''}
                          location={userRow.location ?? ''}
                          onActive={() => {
                            setUser(userRow);
                            setUserDetails(true);
                            setUserEdit(false);
                          }}
                        />
                      </Grid>
                    ))}
                  </Grid>
                </Grid>
              </Fragment>
            ))}
          </Grid>
        </Grid>

        {userDetails && (
          <Grid sx={{ width: 342, margin: { xs: '0 auto', md: 'initial' } }}>
            <UserDetails
              user={user}
              onEditClick={() => {
                setUserDetails(false);
                setUserEdit(true);
              }}
              onClose={() => {
                setUserDetails(false);
                setUserEdit(false);
              }}
            />
          </Grid>
        )}

        {userEdit && (
          <Grid sx={{ width: 342, margin: { xs: '0 auto', md: 'initial' } }}>
            <UserEdit
              user={user}
              onSave={u => {
                if (u.id) setUserDetails(true);
                setUserEdit(false);
                modifyUser(u);
              }}
              onCancel={u => {
                if (u.id) setUserDetails(true);
                setUserEdit(false);
              }}
            />
          </Grid>
        )}
      </Grid>
    </MainCard>
  );
};

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(ContactCardPage);
