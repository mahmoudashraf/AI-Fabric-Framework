'use client';

import AddIcon from '@mui/icons-material/Add';
import DeleteTwoToneIcon from '@mui/icons-material/DeleteTwoTone';
import KeyboardBackspaceIcon from '@mui/icons-material/KeyboardBackspace';
import RemoveIcon from '@mui/icons-material/Remove';
import { Button,
  ButtonGroup,
  Grid,
  IconButton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  useMediaQuery,
 } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import currency from 'currency.js';
import { sum } from 'lodash';
import Link from 'next/link';
import { useEffect, useState } from 'react';

// material-ui

// third-party

// project imports
const gridSpacing = 3;
import { CartCheckoutStateProps, CartProductStateProps } from 'types/cart';
import Avatar from 'ui-component/extended/Avatar';

import ColorOptions from '../ColorOptions';

import CartDiscount from './CartDiscount';
import OrderSummary from './OrderSummary';

// types

// assets

const prodImage = '/assets/images/e-commerce';

// product color select
function getColor(color: string) {
  return ColorOptions.filter(item => item.value === color);
}

// ==============================|| CART - INCREMENT QUANTITY ||============================== //

interface IncrementProps {
  itemId: string | number | undefined;
  quantity: number;
  updateQuantity: (id: string | number | undefined, quantity: number) => void;
}

const Increment = ({ itemId, quantity, updateQuantity }: IncrementProps) => {
  const [value, setValue] = useState(quantity);

  const incrementHandler = () => {
    setValue(value - 1);
    updateQuantity(itemId, value - 1);
  };

  const decrementHandler = () => {
    setValue(value + 1);
    updateQuantity(itemId, value + 1);
  };

  return (
    <ButtonGroup
      size="large"
      variant="text"
      color="inherit"
      sx={{ border: '1px solid', borderColor: 'grey.400' }}
    >
      <Button
        key="three"
        aria-label="increase"
        disabled={value <= 1}
        onClick={incrementHandler}
        sx={{ pr: 0.75, pl: 0.75, minWidth: '0px !important' }}
      >
        <RemoveIcon fontSize="inherit" />
      </Button>
      <Button key="two" sx={{ pl: 0.5, pr: 0.5 }}>
        {value}
      </Button>
      <Button
        key="one"
        aria-label="decrease"
        onClick={decrementHandler}
        sx={{ pl: 0.75, pr: 0.75, minWidth: '0px !important' }}
      >
        <AddIcon fontSize="inherit" />
      </Button>
    </ButtonGroup>
  );
};

// ==============================|| CART - MAIN ||============================== //

interface CartProps {
  checkout: CartCheckoutStateProps;
  onNext: () => void;
  removeProduct: (id: string | number | undefined) => void;
  updateQuantity: (id: string | number | undefined, quantity: number) => void;
}

const Cart = ({ checkout, onNext, removeProduct, updateQuantity }: CartProps) => {
  const theme = useTheme();
  const matchDownMD = useMediaQuery(theme.breakpoints.down('lg'));
  const totalQuantity = sum(checkout.products.map(item => item.quantity));
  const [rows, setRows] = useState(checkout.products);

  useEffect(() => {
    setRows(checkout.products);
  }, [checkout.products]);

  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12 }}>
        <Stack direction="row" alignItems="center" spacing={1}>
          <Typography variant="subtitle1">Cart Item</Typography>
          <Typography variant="caption" sx={{ fontSize: '0.875rem' }}>
            ({totalQuantity})
          </Typography>
        </Stack>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <TableContainer>
          <Table sx={{ minWidth: 650 }} aria-label="simple table">
            <TableHead
              sx={{
                borderTop: '1px solid',
                color: theme.palette.mode === 'dark' ? theme.palette.dark.light + 15 : 'grey.200',
              }}
            >
              <TableRow>
                <TableCell>Product</TableCell>
                <TableCell align="right">Price</TableCell>
                <TableCell align="center">Quantity</TableCell>
                <TableCell align="right">Total</TableCell>
                <TableCell align="right" />
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row: CartProductStateProps, index: number) => {
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
                            alt="product images"
                            variant="rounded"
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
                      <Stack>
                        {row.offerPrice && (
                          <Typography variant="subtitle1">
                            {currency(row.offerPrice).format()}
                          </Typography>
                        )}
                        {row.salePrice && (
                          <Typography variant="caption" sx={{ textDecoration: 'line-through' }}>
                            {currency(row.salePrice).format()}
                          </Typography>
                        )}
                      </Stack>
                    </TableCell>
                    <TableCell align="center">
                      <Increment
                        quantity={row.quantity}
                        itemId={row.itemId}
                        updateQuantity={updateQuantity}
                      />
                    </TableCell>
                    <TableCell align="right">
                      {row.offerPrice && row.quantity && (
                        <Typography variant="subtitle1">
                          {currency(row.offerPrice * row.quantity).format()}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell align="right">
                      <IconButton
                        onClick={() => removeProduct(row.itemId)}
                        size="large"
                        aria-label="product delete"
                      >
                        <DeleteTwoToneIcon sx={{ color: 'grey.500' }} />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <OrderSummary checkout={checkout} />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Grid
          direction={matchDownMD ? 'column-reverse' : 'row'}
          container
          spacing={3}
          alignItems={matchDownMD ? '' : 'center'}
        >
          <Grid size={{ xs: 12 }}>
            <Button
              component={Link}
              href="/apps/e-commerce/products"
              variant="text"
              startIcon={<KeyboardBackspaceIcon />}
            >
              Continue Shopping
            </Button>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Stack spacing={gridSpacing}>
              <CartDiscount />
              <Button variant="contained" fullWidth onClick={onNext}>
                Check Out
              </Button>
            </Stack>
          </Grid>
        </Grid>
      </Grid>
    </Grid>
  );
};

export default Cart;
