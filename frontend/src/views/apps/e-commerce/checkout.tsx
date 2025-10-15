'use client';

import ApartmentIcon from '@mui/icons-material/Apartment';
import CreditCardTwoToneIcon from '@mui/icons-material/CreditCardTwoTone';
import ShoppingCartTwoToneIcon from '@mui/icons-material/ShoppingCartTwoTone';
import { Grid, Tab, Tabs  } from '@mui/material';
import { styled, Theme, useTheme } from '@mui/material/styles';
import { useEffect, useState, ReactNode } from 'react';

// material-ui

// project imports
import BillingAddress from 'components/application/e-commerce/Checkout/BillingAddress';
import Cart from 'components/application/e-commerce/Checkout/Cart';
import CartEmpty from 'components/application/e-commerce/Checkout/CartEmpty';
import Payment from 'components/application/e-commerce/Checkout/Payment';
import useConfig from 'hooks/useConfig';
import { gridSpacing } from 'constants/index';

// Using Context API
import { useCart } from 'contexts/CartContext';
import { useProduct } from 'contexts/ProductContext';
import { useNotifications } from 'contexts/NotificationContext';

// types
import { TabsProps } from 'types';
import { Address } from 'types/e-commerce';
import MainCard from 'ui-component/cards/MainCard';

// assets

interface StyledProps {
  theme: Theme;
  border: number;
  value: number;
  cart: any;
  disabled?: boolean;
  icon?: ReactNode;
  label?: ReactNode;
}

// interface TabOptionProps {
//   label: string;
//   icon: ReactNode;
//   caption: string;
// }

  // @ts-ignore
  const _unused_StyledTab = styled(Tab, {
  shouldForwardProp: (prop) => prop !== 'border' && prop !== 'value' && prop !== 'cart'
})<StyledProps>(({ theme: _theme, border, value, cart }) => ({
    color: cart.checkout.step >= value ? _theme.palette.success.dark : _theme.palette.grey[900],
    minHeight: 'auto',
    minWidth: 250,
    padding: 16,
    borderRadius: `${border}px`,
    display: 'flex',
    flexDirection: 'row',
    alignItems: 'flex-start',
    textAlign: 'left',
    justifyContent: 'flex-start',
    '&:after': {
      backgroundColor: 'transparent !important',
    },
    '&.Mui-selected': {
      color: _theme.palette.primary.main,
      background: _theme.palette.mode === 'dark' ? _theme.palette.dark.main : _theme.palette.grey[50],
    },
    '& > svg': {
      marginBottom: '0px !important',
      marginRight: 10,
      marginTop: 2,
      height: 20,
      width: 20,
    },
    [_theme.breakpoints.down('md')]: {
      minWidth: '100%',
    },
}));

// tabs option (strings only to satisfy strict MUI Tab typings)
const tabLabels: string[] = ['User Profile', 'Billing Address', 'Payment'];
  // @ts-ignore
  const _unused_tabIcons: ReactNode[] = [
  <ShoppingCartTwoToneIcon key="ic0" />,
  <ApartmentIcon key="ic1" />,
  <CreditCardTwoToneIcon key="ic2" />,
];

// tabs
function TabPanel({ children, value, index, ...other }: TabsProps) {
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`simple-tabpanel-${index}`}
      aria-labelledby={`simple-tab-${index}`}
      {...other}
    >
      {value === index && <div>{children}</div>}
    </div>
  );
}

// ==============================|| PRODUCT - CHECKOUT MAIN ||============================== //

const Checkout = () => {
  // @ts-ignore
  const _unused_theme = useTheme();
  const { borderRadius: _borderRadius } = useConfig();

  // Using Context API
  const { 
    state: cartState, 
    removeProduct, 
    updateProduct, 
    setNextStep, 
    setBackStep, 
    setStep, 
    setBilling: _setBilling, 
    setShipping 
  } = useCart();
  const { state: productState, getAddresses, editAddress, addAddress } = useProduct();
  const { showNotifications } = useNotifications();

  const cart = cartState;
  const { addresses } = productState;
  const isCart = cart.checkout.products && cart.checkout.products.length > 0;

  const [value, setValue] = useState(cart.checkout.step > 2 ? 2 : cart.checkout.step);
  const [billing, setBillingLocal] = useState(cart.checkout.billing as Address | null);
  const [address, setAddress] = useState<Address[]>([]);

  useEffect(() => {
    setAddress(addresses);
  }, [addresses]);

  useEffect(() => {
    getAddresses();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const addBillingAddress = (addressNew: Address) => {
    addAddress(addressNew);
  };

  const editBillingAddress = (addressEdit: Address) => {
    editAddress(addressEdit);
  };

  const handleChange = (newValue: number) => {
    setValue(newValue);
    setStep(newValue);
  };

  useEffect(() => {
    setValue(cart.checkout.step > 2 ? 2 : cart.checkout.step);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cart.checkout.step]);

  const removeCartProduct = (id: string | number | undefined) => {
    const nextProducts = cart.checkout.products.filter((p: any) => p.id !== id);
    removeProduct(nextProducts);
    showNotifications({
      message: 'Update Cart Success',
      alert: {
        color: 'success',
        variant: 'filled'
      },
    });
  };

  const updateQuantity = (id: string | number | undefined, quantity: number) => {
    const nextProducts = cart.checkout.products.map((p: any) =>
      p.id === id ? { ...p, quantity } : p
    );
    updateProduct(nextProducts);
  };

  const onNext = () => {
    setNextStep();
  };

  const onBack = () => {
    setBackStep();
  };

  const billingAddressHandler = (addressBilling: Address | null) => {
    if (billing !== null || addressBilling !== null) {
      if (addressBilling !== null) {
        setBillingLocal(addressBilling);
      }

      const chosen = (addressBilling !== null ? addressBilling : billing) as Address | null;
      const mapped: Address | null = chosen
        ? { ...chosen, post: String(chosen.post) as string }
        : null;
      setBillingLocal(mapped);
      onNext();
    } else {
      showNotifications({
        message: 'Please select delivery address',
        alert: {
          color: 'error',
          variant: 'filled'
        },
      });
    }
  };

  const handleShippingCharge = (_type: string) => {
    // map incoming type to numeric charge if needed; for now pass current shipping
    setShipping(cart.checkout.shipping);
  };

  return (
    <MainCard>
      <Grid container spacing={gridSpacing}>
        <Grid size={{ xs: 12 }}>
          <Tabs
            value={value}
            onChange={(_e, newValue) => handleChange(newValue)}
            aria-label="icon label tabs example"
            variant="scrollable"
            sx={{
              '& .MuiTabs-flexContainer': {
                borderBottom: 'none',
              },
              '& .MuiTabs-indicator': {
                display: 'none',
              },
              '& .MuiButtonBase-root + .MuiButtonBase-root': {
                position: 'relative',
                overflow: 'visible',
                ml: 2,
                '&:after': {
                  content: '""',
                  bgcolor: '#ccc',
                  width: 1,
                  height: 'calc(100% - 16px)',
                  position: 'absolute',
                  top: 8,
                  left: -8,
                },
              },
            }}
          >
            {tabLabels.map((label, index) => (
              <Tab
                value={index}
                disabled={index > cart.checkout.step}
                key={index}
                label={label}
              />
            ))}
          </Tabs>
        </Grid>
        <Grid size={{ xs: 12 }}>
          <TabPanel value={value} index={0}>
            {isCart && (
              <Cart
                checkout={{
                  ...cart.checkout,
                  products: cart.checkout.products.map((p: any) => ({
                    ...p,
                    salePrice: Number(p.salePrice ?? 0),
                  })),
                  payment: { ...cart.checkout.payment, card: cart.checkout.payment.card ?? '' }
                } as any}
                onNext={onNext}
                removeProduct={removeCartProduct}
                updateQuantity={updateQuantity}
              />
            )}
            {!isCart && <CartEmpty />}
          </TabPanel>
          <TabPanel value={value} index={1}>
              <BillingAddress
              checkout={{
                ...cart.checkout,
                products: cart.checkout.products.map((p: any) => ({
                  ...p,
                  salePrice: Number(p.salePrice ?? 0),
                })),
                payment: { ...cart.checkout.payment, card: cart.checkout.payment.card ?? '' }
              }}
              onBack={onBack}
              billingAddressHandler={billingAddressHandler}
              address={address}
              addAddress={addBillingAddress}
              editAddress={editBillingAddress}
            />
          </TabPanel>
          <TabPanel value={value} index={2}>
            <Payment
              checkout={{
                ...cart.checkout,
                products: cart.checkout.products.map((p: any) => ({
                  ...p,
                  salePrice: Number(p.salePrice ?? 0),
                })),
                payment: { ...cart.checkout.payment, card: cart.checkout.payment.card ?? '' }
              }}
              onBack={onBack}
              onNext={onNext}
              handleShippingCharge={handleShippingCharge}
            />
          </TabPanel>
        </Grid>
      </Grid>
    </MainCard>
  );
};

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(Checkout);
