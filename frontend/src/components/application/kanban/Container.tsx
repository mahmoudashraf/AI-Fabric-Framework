'use client';

import { Box, Grid, Tab, Tabs  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import React, { useEffect, useState, SyntheticEvent } from 'react';

// material-ui

// project imports
// Using Context API
import { useKanban } from 'contexts/KanbanContext';
import MainCard from 'ui-component/cards/MainCard';
import Loader from 'ui-component/Loader';
function a11yProps(index: number) {
  return {
    id: `simple-tab-${index}`,
    'aria-controls': `simple-tabpanel-${index}`,
  };
}

// ==============================|| APPLICATION - KANBAN ||============================== //
interface Props {
  children: any;
}
function KanbanPage({ children }: Props) {
  // Using Context API
  const {
    getItems,
    getColumns,
    getColumnsOrder,
    getProfiles,
    getComments,
    getUserStory,
    getUserStoryOrder,
  } = useKanban();

  const theme = useTheme();
  const pathname = usePathname();

  const [loading, setLoading] = useState<boolean>(true);

  let selectedTab = 0;
  switch (pathname) {
    case '/apps/kanban/backlogs':
      selectedTab = 1;
      break;
    case '/apps/kanban/board':
    default:
      selectedTab = 0;
  }

  const [value, setValue] = useState(selectedTab);
  const handleChange = (_event: SyntheticEvent, newValue: number) => {
    setValue(newValue);
  };

  useEffect(() => {
    let isMounted = true;
    
    const loadKanbanData = async () => {
      try {
        await Promise.all([
          getItems(),
          getColumns(),
          getColumnsOrder(),
          getProfiles(),
          getComments(),
          getUserStory(),
          getUserStoryOrder()
        ]);
        
        if (isMounted) {
          setLoading(false);
        }
      } catch (error) {
        console.warn('Failed to load Kanban data:', error);
        if (isMounted) {
          setLoading(false);
        }
      }
    };
    
    loadKanbanData();
    
    return () => {
      isMounted = false;
    };
  }, []); // Empty dependency array - only run once on mount

  if (loading) return <Loader />;

  return (
    <Box sx={{ display: 'flex' }}>
      <Grid container>
        <Grid size={{ xs: 12 }}>
          <MainCard contentSX={{ p: 2 }}>
            <Tabs
              value={value}
              variant="scrollable"
              onChange={handleChange}
              sx={{
                px: 1,
                pb: 2,
                '& a': {
                  minWidth: 10,
                  px: 1,
                  py: 1.5,
                  mr: 2.25,
                  color: theme.palette.mode === 'dark' ? 'grey.600' : 'grey.900',
                  display: 'flex',
                  flexDirection: 'row',
                  alignItems: 'center',
                  justifyContent: 'center',
                },
                '& a.Mui-selected': {
                  color: 'primary.main',
                },
                '& a > svg': {
                  marginBottom: '0px !important',
                  mr: 1.25,
                },
              }}
            >
              <Tab
                sx={{ textTransform: 'none' }}
                component={Link}
                href="/apps/kanban/board"
                label={value === 0 ? 'Board' : 'View as Board'}
                {...a11yProps(0)}
              />
              <Tab
                sx={{ textTransform: 'none' }}
                component={Link}
                href="/apps/kanban/backlogs"
                label={value === 1 ? 'Backlogs' : 'View as Backlog'}
                {...a11yProps(1)}
              />
            </Tabs>
            {children}
          </MainCard>
        </Grid>
      </Grid>
    </Box>
  );
}

export default KanbanPage;
