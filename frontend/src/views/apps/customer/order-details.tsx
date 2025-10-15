'use client';

import DescriptionTwoToneIcon from '@mui/icons-material/DescriptionTwoTone';
import LocalShippingTwoToneIcon from '@mui/icons-material/LocalShippingTwoTone';
import ReceiptTwoToneIcon from '@mui/icons-material/ReceiptTwoTone';
import { Box, Tab, Tabs } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import Link from 'next/link';
import { useState } from 'react';

// material-ui

// project imports
import Details from 'components/application/customer/OrderDetails/Details';
import Invoice from 'components/application/customer/OrderDetails/Invoice';
import Status from 'components/application/customer/OrderDetails/Status';
import { TabsProps } from 'types';
import MainCard from 'ui-component/cards/MainCard';

// types

// assets

// tab content
function TabPanel({ children, value, index, ...other }: TabsProps) {
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`simple-tabpanel-${index}`}
      aria-labelledby={`simple-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ p: 0 }}>{children}</Box>}
    </div>
  );
}

function a11yProps(index: number) {
  return {
    id: `simple-tab-${index}`,
    'aria-controls': `simple-tabpanel-${index}`,
  };
}

// ==============================|| ORDER DETAILS ||============================== //

const OrderDetails = () => {
  const theme = useTheme();

  // set selected tab
  const [value, setValue] = useState<number>(0);
  const handleChange = (_event: React.SyntheticEvent<Element, Event>, newValue: number) => {
    setValue(newValue);
  };

  return (
    <MainCard>
      <Tabs
        value={value}
        indicatorColor="primary"
        textColor="primary"
        onChange={handleChange}
        variant="scrollable"
        aria-label="simple tabs example"
        sx={{
          '& a': {
            minHeight: 'auto',
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
            color: theme.palette.primary.main,
          },
          '& a > svg': {
            marginBottom: '0px !important',
            marginRight: 1.25,
          },
          mb: 3,
        }}
      >
        <Tab
          icon={<DescriptionTwoToneIcon />}
          component={Link}
          href="#"
          label="Details"
          {...a11yProps(0)}
        />
        <Tab
          icon={<ReceiptTwoToneIcon />}
          component={Link}
          href="#"
          label="Invoice"
          {...a11yProps(1)}
        />
        <Tab
          icon={<LocalShippingTwoToneIcon />}
          component={Link}
          href="#"
          label="Status"
          {...a11yProps(2)}
        />
      </Tabs>

      {/* tab - details */}
      <TabPanel value={value} index={0}>
        <Details />
      </TabPanel>

      {/* tab - invoice */}
      <TabPanel value={value} index={1}>
        <Invoice />
      </TabPanel>

      {/* tab - status */}
      <TabPanel value={value} index={2}>
        <Status />
      </TabPanel>
    </MainCard>
  );
};

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(OrderDetails);
