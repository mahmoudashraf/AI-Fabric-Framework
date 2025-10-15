'use client';

import AddTwoToneIcon from '@mui/icons-material/AddTwoTone';
import KeyboardBackspaceIcon from '@mui/icons-material/KeyboardBackspace';
import { Box,
  Button,
  FormControl,
  FormControlLabel,
  Grid,
  RadioGroup,
  Radio,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableRow,
  Typography,
 } from '@mui/material';
import currency from 'currency.js';
import { useEffect, useState } from 'react';

// material-ui

// third-party

// project imports
const gridSpacing = 3;
import { useCartActions } from 'contexts/CartContext';
import { useSnackbar } from 'contexts/NotificationContext';
import { CartCheckoutStateProps } from 'types/cart';
import SubCard from 'ui-component/cards/SubCard';
import ColorOptions from '../ColorOptions';
import AddressCard from './AddressCard';
import OrderSummary from './OrderSummary';
import PaymentSelect from './PaymentSelect';


import PaymentOptions from './PaymentOptions';
import PaymentCard from './PaymentCard';
import AddPaymentCard from './AddPaymentCard';
import OrderComplete from './OrderComplete';

import Avatar from 'ui-component/extended/Avatar';

// types
import { PaymentOptionsProps } from 'types/e-commerce';

// assets

const prodImage = '/assets/images/e-commerce';

// product color select
function getColor(color: string) {
  return ColorOptions.filter(item => item.value === color);
}

// ==============================|| CHECKOUT PAYMENT - MAIN ||============================== //

interface PaymentProps {
  checkout: CartCheckoutStateProps;
  onBack: () => void;
  onNext: () => void;
  handleShippingCharge: (type: string) => void;
}

const Payment = ({ checkout, onBack, onNext, handleShippingCharge }: PaymentProps) => {
  const { setPayment: setPaymentAction } = useCartActions();
  const { showSnackbar } = useSnackbar();

  const [type, setType] = useState(checkout.payment.type);
  const [payment, setPayment] = useState(checkout.payment.method);
  const [rows, setRows] = useState(checkout.products);
  const [cards, setCards] = useState(checkout.payment.card);

  const [open, setOpen] = useState(false);
  const handleClickOpen = () => {
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
  };

  const [complete, setComplete] = useState(false);

  useEffect(() => {
    if (checkout.step > 2) {
      setComplete(true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    setRows(checkout.products);
  }, [checkout.products]);

  const cardHandler = (card: string) => {
    if (payment === 'card') {
      setCards(card);
      setPaymentAction({ type: checkout.payment.type as "paypal" | "free" | "credit_card", method: checkout.payment.method as "paypal" | "cod" | "credit" | "bank", card: card as "paypal" | "free" | "credit_card" });
    }
  };

  const handlePaymentMethod = (value: "paypal" | "cod" | "credit" | "bank") => {
    setPayment(value);
    setPaymentAction({ type: checkout.payment.type as "paypal" | "free" | "credit_card", method: value, card: checkout.payment.card });
  };

  const completeHandler = () => {
    if (payment === 'card' && (cards === '' || cards === null)) {
      showSnackbar('Select Payment Card', {
        variant: 'error' as const,
        alert: {
          color: 'error' as const,
          variant: 'filled' as const,
        },
        close: false,
      });
    } else {
      onNext();
      setComplete(true);
    }
  };

  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12 }}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12 }}>
            <Stack>
              <Typography variant="subtitle1">Delivery Options</Typography>
              <FormControl>
                <RadioGroup
                  row
                  aria-label="delivery-options"
                  value={type}
                  onChange={e => {
                    setType(e.target.value);
                    handleShippingCharge(e.target.value);
                  }}
                  name="delivery-options"
                >
                  <Grid container spacing={gridSpacing} alignItems="center" sx={{ pt: 2 }}>
                    <Grid size={{ xs: 12 }}>
                      <SubCard content={false}>
                        <Box sx={{ p: 2 }}>
                          <FormControlLabel
                            value="free"
                            control={<Radio />}
                            label={
                              <Stack spacing={0.25}>
                                <Typography variant="subtitle1">
                                  Standard Delivery (Free)
                                </Typography>
                                <Typography variant="caption">Delivered on Monday 8 Jun</Typography>
                              </Stack>
                            }
                            sx={{ '& .MuiSvgIcon-root': { fontSize: 32 } }}
                          />
                        </Box>
                      </SubCard>
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <SubCard content={false}>
                        <Box sx={{ p: 2 }}>
                          <FormControlLabel
                            value="fast"
                            control={<Radio />}
                            label={
                              <Stack spacing={0.25}>
                                <Typography variant="subtitle1">Fast Delivery ($5.00)</Typography>
                                <Typography variant="caption">Delivered on Friday 5 Jun</Typography>
                              </Stack>
                            }
                            sx={{ '& .MuiSvgIcon-root': { fontSize: 32 } }}
                          />
                        </Box>
                      </SubCard>
                    </Grid>
                  </Grid>
                </RadioGroup>
              </FormControl>
            </Stack>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Typography variant="subtitle1">Payment Options</Typography>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <FormControl>
              <RadioGroup
                aria-label="delivery-options"
                value={payment}
                onChange={e => handlePaymentMethod(e.target.value as "paypal" | "cod" | "credit" | "bank")}
                name="delivery-options"
              >
                <Grid container spacing={gridSpacing} alignItems="center">
                  {PaymentOptions.map((item: PaymentOptionsProps, index) => (
                    <Grid size={{ xs: 12 }} key={index}>
                      <PaymentSelect item={item} />
                    </Grid>
                  ))}
                </Grid>
              </RadioGroup>
            </FormControl>
          </Grid>
          <Grid size={{ xs: 12 }} sx={{ opacity: payment === 'card' ? 1 : 0.4 }}>
            <SubCard
              title="Add Your Card"
              secondary={
                <Button
                  variant="contained"
                  size="small"
                  startIcon={<AddTwoToneIcon />}
                  onClick={handleClickOpen}
                >
                  Add Card
                </Button>
              }
            >
              <Grid container spacing={gridSpacing}>
                <Grid size={{ xs: 12, sm: 6, md: 12, lg: 6 }}>
                  <PaymentCard type="mastercard" cards={cards} cardHandler={cardHandler} />
                </Grid>
                <Grid size={{ xs: 12, sm: 6, md: 12, lg: 6 }}>
                  <PaymentCard type="visa" cards={cards} cardHandler={cardHandler} />
                </Grid>
              </Grid>
              <AddPaymentCard open={open} handleClose={handleClose} />
            </SubCard>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Grid container spacing={3} alignItems="center" justifyContent="space-between">
              <Grid>
                <Button variant="text" startIcon={<KeyboardBackspaceIcon />} onClick={onBack}>
                  Back
                </Button>
              </Grid>
              <Grid>
                <Button variant="contained" onClick={completeHandler}>
                  Complete Order
                </Button>
                <OrderComplete open={complete} />
              </Grid>
            </Grid>
          </Grid>
        </Grid>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12 }}>
            <Stack>
              <Typography variant="subtitle1" sx={{ pb: 2 }}>
                Cart Items
              </Typography>
              <TableContainer>
                <Table sx={{ minWidth: 280 }} aria-label="simple table">
                  <TableBody>
                    {rows.map((row, index) => {
                      const colorsData = row.color ? getColor(row.color) : false;
                      return (
                        <TableRow
                          key={index}
                          sx={{ '&:last-of-type td, &:last-of-type th': { border: 0 } }}
                        >
                          <TableCell component="th" scope="row">
                            <Grid container alignItems="center" spacing={2}>
                              <Grid>
                                <Avatar
                                  size="md"
                                  variant="rounded"
                                  alt="product images"
                                  src={row.image ? `${prodImage}/${row.image}` : ''}
                                />
                              </Grid>
                              <Grid>
                                <Stack spacing={0}>
                                  <Typography variant="subtitle1">{row.name}</Typography>
                                  <Stack direction="row" alignItems="center" spacing={1}>
                                    <Typography variant="subtitle2" sx={{ fontWeight: 500 }}>
                                      Size:{' '}
                                      <Typography variant="caption" component="span">
                                        {row.size}
                                      </Typography>
                                    </Typography>
                                    <Typography variant="caption" sx={{ fontSize: '1rem' }}>
                                      |
                                    </Typography>
                                    <Typography variant="subtitle2" sx={{ fontWeight: 500 }}>
                                      Color:{' '}
                                      <Typography variant="caption" component="span">
                                        {Array.isArray(colorsData) ? colorsData[0]?.label : 'Multicolor'}
                                      </Typography>
                                    </Typography>
                                  </Stack>
                                </Stack>
                              </Grid>
                            </Grid>
                          </TableCell>
                          <TableCell align="right">
                            {row.offerPrice && row.quantity && (
                              <Typography variant="subtitle1">
                                {currency(row.offerPrice * row.quantity).format()}
                              </Typography>
                            )}
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            </Stack>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <OrderSummary checkout={checkout} />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <AddressCard single change address={checkout.billing} onBack={onBack} />
          </Grid>
        </Grid>
      </Grid>
    </Grid>
  );
};

export default Payment;
