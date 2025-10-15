// material-ui
import { Alert, Button, Checkbox, FormControlLabel, Grid, Stack, TextField, Typography } from '@mui/material';

// project imports
import { useAdvancedForm } from '@/hooks/enterprise';
import { useNotifications } from 'contexts/NotificationContext';
import type { IValidationRule } from '@/types/common';
import AnimateButton from 'ui-component/extended/AnimateButton';

// ==============================|| FORM WIZARD - VALIDATION ENTERPRISE ||============================== //

export type PaymentData = { cardName: string | undefined; cardNumber: number | undefined };

// Enterprise Pattern: Type-safe form interface
interface PaymentFormData {
  cardName: string;
  cardNumber: string;
  expDate: string;
  cvv: string;
  saveCard: boolean;
}

// Enterprise Pattern: Form validation rules
const validationRules: Partial<Record<keyof PaymentFormData, IValidationRule[]>> = {
  cardName: [
    { type: 'required', message: 'Name on card is required' },
    { type: 'minLength', value: 2, message: 'Name must be at least 2 characters' },
  ],
  cardNumber: [
    { type: 'required', message: 'Card number is required' },
    { type: 'pattern', value: /^\d{4}\s?\d{4}\s?\d{4}\s?\d{4}$/, message: 'Invalid card number format' },
  ],
  expDate: [
    { type: 'required', message: 'Expiry date is required' },
    { type: 'pattern', value: /^(0[1-9]|1[0-2])\/\d{2}$/, message: 'Invalid expiry date format (MM/YY)' },
  ],
  cvv: [
    { type: 'required', message: 'CVV is required' },
    { type: 'pattern', value: /^\d{3,4}$/, message: 'CVV must be 3-4 digits' },
  ],
};

interface PaymentFormProps {
  paymentData: PaymentData;
  setPaymentData: (d: PaymentData) => void;
  handleNext: () => void;
  handleBack: () => void;
  setErrorIndex: (i: number | null) => void;
}

export default function PaymentForm({
  paymentData,
  setPaymentData,
  handleNext,
  handleBack,
  setErrorIndex,
}: PaymentFormProps) {
  const notifications = useNotifications();

  // Enterprise Pattern: Advanced form hook with validation
  const form = useAdvancedForm<PaymentFormData>({
    initialValues: {
      cardName: paymentData.cardName || '',
      cardNumber: paymentData.cardNumber?.toString() || '',
      expDate: '',
      cvv: '',
      saveCard: false,
    },
    validationRules,
    validateOnChange: true,
    validateOnBlur: true,
    onSubmit: async (values) => {
      try {
        // Enterprise Pattern: Simulated API call (replace with actual API)
        await new Promise((resolve) => setTimeout(resolve, 1000));
        console.log('Payment form submitted:', { ...values, cardNumber: '***', cvv: '***' });

        // Update payment data
        setPaymentData({
          cardName: values.cardName || undefined,
          cardNumber: Number(values.cardNumber) || undefined,
        });

        // Success notification
        notifications.showNotification({
          message: 'Payment information saved successfully',
          variant: 'success',
          alert: { color: 'success', variant: 'filled' },
          close: true,
        });

        // Proceed to next step
        handleNext();
      } catch (error) {
        // Error notification
        notifications.showNotification({
          message: 'Failed to save payment information. Please try again.',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
        setErrorIndex(1);
      }
    },
  });

  return (
    <>
      <Typography variant="h5" gutterBottom sx={{ mb: 2 }}>
        Payment method
      </Typography>
      <form onSubmit={form.handleSubmit()}>
        <Grid container spacing={3}>
          <Grid size={{ xs: 12 }}>
            <TextField
              id="cardName"
              name="cardName"
              value={form.values.cardName}
              onChange={form.handleChange('cardName')}
              onBlur={form.handleBlur('cardName')}
              error={Boolean(form.touched.cardName && form.errors.cardName)}
              helperText={form.touched.cardName && form.errors.cardName}
              label="Name on card"
              fullWidth
              autoComplete="cc-name"
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField
              id="cardNumber"
              name="cardNumber"
              label="Card number"
              value={form.values.cardNumber}
              onChange={form.handleChange('cardNumber')}
              onBlur={form.handleBlur('cardNumber')}
              error={Boolean(form.touched.cardNumber && form.errors.cardNumber)}
              helperText={form.touched.cardNumber && form.errors.cardNumber}
              fullWidth
              autoComplete="cc-number"
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField 
              id="expDate" 
              label="Expiry date" 
              value={form.values.expDate}
              onChange={form.handleChange('expDate')}
              onBlur={form.handleBlur('expDate')}
              error={Boolean(form.touched.expDate && form.errors.expDate)}
              helperText={form.touched.expDate && form.errors.expDate}
              fullWidth 
              autoComplete="cc-exp" 
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField
              id="cvv"
              label="CVV"
              value={form.values.cvv}
              onChange={form.handleChange('cvv')}
              onBlur={form.handleBlur('cvv')}
              error={Boolean(form.touched.cvv && form.errors.cvv)}
              helperText={form.touched.cvv ? form.errors.cvv : 'Last three digits on signature strip'}
              fullWidth
              autoComplete="cc-csc"
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <FormControlLabel
              control={
                <Checkbox 
                  color="secondary" 
                  name="saveCard" 
                  checked={form.values.saveCard}
                  onChange={(event) => form.setValue('saveCard', event.target.checked)}
                />
              }
              label="Remember credit card details for next time"
            />
          </Grid>

          {/* Enterprise Pattern: Form status feedback */}
          {Object.keys(form.errors).length > 0 && Object.keys(form.touched).length > 0 && (
            <Grid size={{ xs: 12 }}>
              <Alert severity="error">Please fix the errors above before submitting</Alert>
            </Grid>
          )}

          <Grid size={{ xs: 12 }}>
            <Stack direction="row" justifyContent="space-between">
              <Button onClick={handleBack} sx={{ my: 3, ml: 1 }}>
                Back
              </Button>
              <AnimateButton>
                <Button
                  variant="contained"
                  type="submit"
                  sx={{ my: 3, ml: 1 }}
                  disabled={form.isSubmitting || !form.isValid}
                  onClick={() => setErrorIndex(1)}
                >
                  {form.isSubmitting ? 'Saving...' : 'Next'}
                </Button>
              </AnimateButton>
            </Stack>
          </Grid>
        </Grid>
      </form>
    </>
  );
}
