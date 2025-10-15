# Form Migration Examples

This document provides step-by-step migration examples from Formik to the modern `useAdvancedForm` hook.

## Example 1: Basic Login Form

### Before (Formik)

```typescript
import React from 'react';
import { useFormik } from 'formik';
import * as yup from 'yup';
import { TextField, Button, Box } from '@mui/material';

const validationSchema = yup.object({
  email: yup.string().email('Invalid email').required('Email is required'),
  password: yup.string().min(8, 'Password must be at least 8 characters').required('Password is required'),
});

const LoginForm = () => {
  const formik = useFormik({
    initialValues: {
      email: '',
      password: '',
    },
    validationSchema,
    onSubmit: (values) => {
      console.log(values);
    },
  });

  return (
    <Box component="form" onSubmit={formik.handleSubmit} sx={{ mt: 1 }}>
      <TextField
        margin="normal"
        required
        fullWidth
        id="email"
        label="Email Address"
        name="email"
        autoComplete="email"
        autoFocus
        value={formik.values.email}
        onChange={formik.handleChange}
        error={formik.touched.email && Boolean(formik.errors.email)}
        helperText={formik.touched.email && formik.errors.email}
      />
      <TextField
        margin="normal"
        required
        fullWidth
        name="password"
        label="Password"
        type="password"
        id="password"
        autoComplete="current-password"
        value={formik.values.password}
        onChange={formik.handleChange}
        error={formik.touched.password && Boolean(formik.errors.password)}
        helperText={formik.touched.password && formik.errors.password}
      />
      <Button
        type="submit"
        fullWidth
        variant="contained"
        sx={{ mt: 3, mb: 2 }}
      >
        Sign In
      </Button>
    </Box>
  );
};

export default LoginForm;
```

### After (useAdvancedForm)

```typescript
import React from 'react';
import { TextField, Button, Box } from '@mui/material';
import { useAdvancedForm } from '@/hooks/enterprise';
import { useNotifications } from 'contexts/NotificationContext';
import { createValidationRules } from '@/utils/validationHelpers';
import type { IValidationRule } from '@/types/common';

interface LoginFormData {
  email: string;
  password: string;
}

const validationRules: Partial<Record<keyof LoginFormData, IValidationRule[]>> = {
  email: [
    { type: 'required', message: 'Email is required' },
    { type: 'email', message: 'Invalid email' },
  ],
  password: [
    { type: 'required', message: 'Password is required' },
    { type: 'minLength', value: 8, message: 'Password must be at least 8 characters' },
  ],
};

const LoginForm = () => {
  const notifications = useNotifications();
  
  const form = useAdvancedForm<LoginFormData>({
    initialValues: {
      email: '',
      password: '',
    },
    validationRules,
    validateOnChange: true,
    validateOnBlur: true,
    onSubmit: async (values) => {
      try {
        // Simulate API call
        await new Promise(resolve => setTimeout(resolve, 1000));
        console.log(values);
        
        notifications.showNotification({
          message: 'Login successful!',
          variant: 'success',
          alert: { color: 'success', variant: 'filled' },
          close: true,
        });
      } catch (error) {
        notifications.showNotification({
          message: 'Login failed. Please try again.',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      }
    },
  });

  return (
    <Box component="form" onSubmit={form.handleSubmit()} sx={{ mt: 1 }}>
      <TextField
        margin="normal"
        required
        fullWidth
        id="email"
        label="Email Address"
        name="email"
        autoComplete="email"
        autoFocus
        value={form.values.email}
        onChange={form.handleChange('email')}
        onBlur={form.handleBlur('email')}
        error={Boolean(form.touched.email && form.errors.email)}
        helperText={form.touched.email && form.errors.email}
      />
      <TextField
        margin="normal"
        required
        fullWidth
        name="password"
        label="Password"
        type="password"
        id="password"
        autoComplete="current-password"
        value={form.values.password}
        onChange={form.handleChange('password')}
        onBlur={form.handleBlur('password')}
        error={Boolean(form.touched.password && form.errors.password)}
        helperText={form.touched.password && form.errors.password}
      />
      <Button
        type="submit"
        fullWidth
        variant="contained"
        disabled={form.isSubmitting || !form.isValid}
        sx={{ mt: 3, mb: 2 }}
      >
        {form.isSubmitting ? 'Signing in...' : 'Sign In'}
      </Button>
    </Box>
  );
};

export default LoginForm;
```

### Migration Steps

1. **Replace imports**
   ```typescript
   // Remove
   import { useFormik } from 'formik';
   import * as yup from 'yup';
   
   // Add
   import { useAdvancedForm } from '@/hooks/enterprise';
   import { useNotifications } from 'contexts/NotificationContext';
   import { createValidationRules } from '@/utils/validationHelpers';
   import type { IValidationRule } from '@/types/common';
   ```

2. **Define form interface**
   ```typescript
   interface LoginFormData {
     email: string;
     password: string;
   }
   ```

3. **Convert validation schema**
   ```typescript
   // From Yup
   const validationSchema = yup.object({
     email: yup.string().email('Invalid email').required('Email is required'),
     password: yup.string().min(8, 'Password must be at least 8 characters').required('Password is required'),
   });
   
   // To validation rules
   const validationRules: Partial<Record<keyof LoginFormData, IValidationRule[]>> = {
     email: [
       { type: 'required', message: 'Email is required' },
       { type: 'email', message: 'Invalid email' },
     ],
     password: [
       { type: 'required', message: 'Password is required' },
       { type: 'minLength', value: 8, message: 'Password must be at least 8 characters' },
     ],
   };
   ```

4. **Replace useFormik with useAdvancedForm**
   ```typescript
   // From
   const formik = useFormik({
     initialValues: { email: '', password: '' },
     validationSchema,
     onSubmit: (values) => { console.log(values); },
   });
   
   // To
   const form = useAdvancedForm<LoginFormData>({
     initialValues: { email: '', password: '' },
     validationRules,
     validateOnChange: true,
     validateOnBlur: true,
     onSubmit: async (values) => {
       try {
         await submitData(values);
         // Success handling
       } catch (error) {
         // Error handling
       }
     },
   });
   ```

5. **Update field bindings**
   ```typescript
   // From
   value={formik.values.email}
   onChange={formik.handleChange}
   error={formik.touched.email && Boolean(formik.errors.email)}
   helperText={formik.touched.email && formik.errors.email}
   
   // To
   value={form.values.email}
   onChange={form.handleChange('email')}
   onBlur={form.handleBlur('email')}
   error={Boolean(form.touched.email && form.errors.email)}
   helperText={form.touched.email && form.errors.email}
   ```

6. **Add loading states and notifications**
   ```typescript
   // Add loading state to button
   disabled={form.isSubmitting || !form.isValid}
   {form.isSubmitting ? 'Signing in...' : 'Sign In'}
   
   // Add notifications for success/error
   ```

## Example 2: Registration Form with Complex Validation

### Before (Formik)

```typescript
import React from 'react';
import { useFormik } from 'formik';
import * as yup from 'yup';
import { TextField, Button, Box, FormControlLabel, Checkbox } from '@mui/material';

const validationSchema = yup.object({
  firstName: yup.string().required('First name is required'),
  lastName: yup.string().required('Last name is required'),
  email: yup.string().email('Invalid email').required('Email is required'),
  password: yup.string()
    .min(8, 'Password must be at least 8 characters')
    .matches(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/, 'Password must contain uppercase, lowercase and number')
    .required('Password is required'),
  confirmPassword: yup.string()
    .oneOf([yup.ref('password')], 'Passwords must match')
    .required('Please confirm your password'),
  agreeToTerms: yup.boolean().oneOf([true], 'You must agree to the terms'),
});

const RegistrationForm = () => {
  const formik = useFormik({
    initialValues: {
      firstName: '',
      lastName: '',
      email: '',
      password: '',
      confirmPassword: '',
      agreeToTerms: false,
    },
    validationSchema,
    onSubmit: (values) => {
      console.log(values);
    },
  });

  return (
    <Box component="form" onSubmit={formik.handleSubmit} sx={{ mt: 1 }}>
      <TextField
        margin="normal"
        required
        fullWidth
        id="firstName"
        label="First Name"
        name="firstName"
        autoComplete="given-name"
        autoFocus
        value={formik.values.firstName}
        onChange={formik.handleChange}
        error={formik.touched.firstName && Boolean(formik.errors.firstName)}
        helperText={formik.touched.firstName && formik.errors.firstName}
      />
      <TextField
        margin="normal"
        required
        fullWidth
        id="lastName"
        label="Last Name"
        name="lastName"
        autoComplete="family-name"
        value={formik.values.lastName}
        onChange={formik.handleChange}
        error={formik.touched.lastName && Boolean(formik.errors.lastName)}
        helperText={formik.touched.lastName && formik.errors.lastName}
      />
      <TextField
        margin="normal"
        required
        fullWidth
        id="email"
        label="Email Address"
        name="email"
        autoComplete="email"
        value={formik.values.email}
        onChange={formik.handleChange}
        error={formik.touched.email && Boolean(formik.errors.email)}
        helperText={formik.touched.email && formik.errors.email}
      />
      <TextField
        margin="normal"
        required
        fullWidth
        name="password"
        label="Password"
        type="password"
        id="password"
        autoComplete="new-password"
        value={formik.values.password}
        onChange={formik.handleChange}
        error={formik.touched.password && Boolean(formik.errors.password)}
        helperText={formik.touched.password && formik.errors.password}
      />
      <TextField
        margin="normal"
        required
        fullWidth
        name="confirmPassword"
        label="Confirm Password"
        type="password"
        id="confirmPassword"
        autoComplete="new-password"
        value={formik.values.confirmPassword}
        onChange={formik.handleChange}
        error={formik.touched.confirmPassword && Boolean(formik.errors.confirmPassword)}
        helperText={formik.touched.confirmPassword && formik.errors.confirmPassword}
      />
      <FormControlLabel
        control={
          <Checkbox
            name="agreeToTerms"
            checked={formik.values.agreeToTerms}
            onChange={formik.handleChange}
            color="primary"
          />
        }
        label="I agree to the terms and conditions"
      />
      {formik.touched.agreeToTerms && formik.errors.agreeToTerms && (
        <div style={{ color: 'red', fontSize: '0.75rem', marginTop: '4px' }}>
          {formik.errors.agreeToTerms}
        </div>
      )}
      <Button
        type="submit"
        fullWidth
        variant="contained"
        sx={{ mt: 3, mb: 2 }}
      >
        Register
      </Button>
    </Box>
  );
};

export default RegistrationForm;
```

### After (useAdvancedForm)

```typescript
import React from 'react';
import { TextField, Button, Box, FormControlLabel, Checkbox, Alert } from '@mui/material';
import { useAdvancedForm } from '@/hooks/enterprise';
import { useNotifications } from 'contexts/NotificationContext';
import { createValidationRules } from '@/utils/validationHelpers';
import type { IValidationRule } from '@/types/common';

interface RegistrationFormData {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  confirmPassword: string;
  agreeToTerms: boolean;
}

const validationRules: Partial<Record<keyof RegistrationFormData, IValidationRule[]>> = {
  firstName: [
    { type: 'required', message: 'First name is required' },
    { type: 'name', message: 'Please enter a valid first name' },
  ],
  lastName: [
    { type: 'required', message: 'Last name is required' },
    { type: 'name', message: 'Please enter a valid last name' },
  ],
  email: [
    { type: 'required', message: 'Email is required' },
    { type: 'emailStrict', message: 'Invalid email' },
  ],
  password: [
    { type: 'required', message: 'Password is required' },
    { type: 'passwordStrong', message: 'Password must contain uppercase, lowercase, number and special character' },
  ],
  confirmPassword: [
    { type: 'required', message: 'Please confirm your password' },
    { 
      type: 'custom', 
      message: 'Passwords must match',
      validator: (value, formValues) => value === formValues.password
    },
  ],
  agreeToTerms: [
    { 
      type: 'custom', 
      message: 'You must agree to the terms',
      validator: (value) => value === true
    },
  ],
};

const RegistrationForm = () => {
  const notifications = useNotifications();
  
  const form = useAdvancedForm<RegistrationFormData>({
    initialValues: {
      firstName: '',
      lastName: '',
      email: '',
      password: '',
      confirmPassword: '',
      agreeToTerms: false,
    },
    validationRules,
    validateOnChange: true,
    validateOnBlur: true,
    onSubmit: async (values) => {
      try {
        // Simulate API call
        await new Promise(resolve => setTimeout(resolve, 1000));
        console.log(values);
        
        notifications.showNotification({
          message: 'Registration successful!',
          variant: 'success',
          alert: { color: 'success', variant: 'filled' },
          close: true,
        });
      } catch (error) {
        notifications.showNotification({
          message: 'Registration failed. Please try again.',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      }
    },
  });

  return (
    <Box component="form" onSubmit={form.handleSubmit()} sx={{ mt: 1 }}>
      <TextField
        margin="normal"
        required
        fullWidth
        id="firstName"
        label="First Name"
        name="firstName"
        autoComplete="given-name"
        autoFocus
        value={form.values.firstName}
        onChange={form.handleChange('firstName')}
        onBlur={form.handleBlur('firstName')}
        error={Boolean(form.touched.firstName && form.errors.firstName)}
        helperText={form.touched.firstName && form.errors.firstName}
      />
      <TextField
        margin="normal"
        required
        fullWidth
        id="lastName"
        label="Last Name"
        name="lastName"
        autoComplete="family-name"
        value={form.values.lastName}
        onChange={form.handleChange('lastName')}
        onBlur={form.handleBlur('lastName')}
        error={Boolean(form.touched.lastName && form.errors.lastName)}
        helperText={form.touched.lastName && form.errors.lastName}
      />
      <TextField
        margin="normal"
        required
        fullWidth
        id="email"
        label="Email Address"
        name="email"
        autoComplete="email"
        value={form.values.email}
        onChange={form.handleChange('email')}
        onBlur={form.handleBlur('email')}
        error={Boolean(form.touched.email && form.errors.email)}
        helperText={form.touched.email && form.errors.email}
      />
      <TextField
        margin="normal"
        required
        fullWidth
        name="password"
        label="Password"
        type="password"
        id="password"
        autoComplete="new-password"
        value={form.values.password}
        onChange={form.handleChange('password')}
        onBlur={form.handleBlur('password')}
        error={Boolean(form.touched.password && form.errors.password)}
        helperText={form.touched.password && form.errors.password}
      />
      <TextField
        margin="normal"
        required
        fullWidth
        name="confirmPassword"
        label="Confirm Password"
        type="password"
        id="confirmPassword"
        autoComplete="new-password"
        value={form.values.confirmPassword}
        onChange={form.handleChange('confirmPassword')}
        onBlur={form.handleBlur('confirmPassword')}
        error={Boolean(form.touched.confirmPassword && form.errors.confirmPassword)}
        helperText={form.touched.confirmPassword && form.errors.confirmPassword}
      />
      <FormControlLabel
        control={
          <Checkbox
            name="agreeToTerms"
            checked={form.values.agreeToTerms}
            onChange={(event) => form.setValue('agreeToTerms', event.target.checked)}
            color="primary"
          />
        }
        label="I agree to the terms and conditions"
      />
      {form.touched.agreeToTerms && form.errors.agreeToTerms && (
        <Alert severity="error" sx={{ mt: 1 }}>
          {form.errors.agreeToTerms}
        </Alert>
      )}
      
      {/* Form status feedback */}
      {Object.keys(form.errors).length > 0 && Object.keys(form.touched).length > 0 && (
        <Alert severity="error" sx={{ mt: 2 }}>
          Please fix the errors above before submitting
        </Alert>
      )}
      
      <Button
        type="submit"
        fullWidth
        variant="contained"
        disabled={form.isSubmitting || !form.isValid}
        sx={{ mt: 3, mb: 2 }}
      >
        {form.isSubmitting ? 'Registering...' : 'Register'}
      </Button>
    </Box>
  );
};

export default RegistrationForm;
```

## Example 3: Multi-Step Wizard Form

### Before (Formik)

```typescript
import React, { useState } from 'react';
import { useFormik } from 'formik';
import * as yup from 'yup';
import { TextField, Button, Box, Stepper, Step, StepLabel, Grid } from '@mui/material';

const validationSchema = yup.object({
  firstName: yup.string().required('First name is required'),
  lastName: yup.string().required('Last name is required'),
  email: yup.string().email('Invalid email').required('Email is required'),
  address: yup.string().required('Address is required'),
  city: yup.string().required('City is required'),
  zipCode: yup.string().required('ZIP code is required'),
  cardNumber: yup.string().required('Card number is required'),
  expiryDate: yup.string().required('Expiry date is required'),
  cvv: yup.string().required('CVV is required'),
});

const WizardForm = () => {
  const [activeStep, setActiveStep] = useState(0);
  
  const formik = useFormik({
    initialValues: {
      firstName: '',
      lastName: '',
      email: '',
      address: '',
      city: '',
      zipCode: '',
      cardNumber: '',
      expiryDate: '',
      cvv: '',
    },
    validationSchema,
    onSubmit: (values) => {
      console.log(values);
    },
  });

  const handleNext = () => {
    setActiveStep(activeStep + 1);
  };

  const handleBack = () => {
    setActiveStep(activeStep - 1);
  };

  const renderStepContent = (step: number) => {
    switch (step) {
      case 0:
        return (
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="First Name"
                name="firstName"
                value={formik.values.firstName}
                onChange={formik.handleChange}
                error={formik.touched.firstName && Boolean(formik.errors.firstName)}
                helperText={formik.touched.firstName && formik.errors.firstName}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Last Name"
                name="lastName"
                value={formik.values.lastName}
                onChange={formik.handleChange}
                error={formik.touched.lastName && Boolean(formik.errors.lastName)}
                helperText={formik.touched.lastName && formik.errors.lastName}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Email"
                name="email"
                value={formik.values.email}
                onChange={formik.handleChange}
                error={formik.touched.email && Boolean(formik.errors.email)}
                helperText={formik.touched.email && formik.errors.email}
              />
            </Grid>
          </Grid>
        );
      case 1:
        return (
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Address"
                name="address"
                value={formik.values.address}
                onChange={formik.handleChange}
                error={formik.touched.address && Boolean(formik.errors.address)}
                helperText={formik.touched.address && formik.errors.address}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="City"
                name="city"
                value={formik.values.city}
                onChange={formik.handleChange}
                error={formik.touched.city && Boolean(formik.errors.city)}
                helperText={formik.touched.city && formik.errors.city}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="ZIP Code"
                name="zipCode"
                value={formik.values.zipCode}
                onChange={formik.handleChange}
                error={formik.touched.zipCode && Boolean(formik.errors.zipCode)}
                helperText={formik.touched.zipCode && formik.errors.zipCode}
              />
            </Grid>
          </Grid>
        );
      case 2:
        return (
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Card Number"
                name="cardNumber"
                value={formik.values.cardNumber}
                onChange={formik.handleChange}
                error={formik.touched.cardNumber && Boolean(formik.errors.cardNumber)}
                helperText={formik.touched.cardNumber && formik.errors.cardNumber}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Expiry Date"
                name="expiryDate"
                value={formik.values.expiryDate}
                onChange={formik.handleChange}
                error={formik.touched.expiryDate && Boolean(formik.errors.expiryDate)}
                helperText={formik.touched.expiryDate && formik.errors.expiryDate}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="CVV"
                name="cvv"
                value={formik.values.cvv}
                onChange={formik.handleChange}
                error={formik.touched.cvv && Boolean(formik.errors.cvv)}
                helperText={formik.touched.cvv && formik.errors.cvv}
              />
            </Grid>
          </Grid>
        );
      default:
        return null;
    }
  };

  return (
    <Box>
      <Stepper activeStep={activeStep}>
        <Step><StepLabel>Personal Info</StepLabel></Step>
        <Step><StepLabel>Address</StepLabel></Step>
        <Step><StepLabel>Payment</StepLabel></Step>
      </Stepper>
      
      <Box sx={{ mt: 3 }}>
        {renderStepContent(activeStep)}
      </Box>
      
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 3 }}>
        <Button
          disabled={activeStep === 0}
          onClick={handleBack}
        >
          Back
        </Button>
        {activeStep === 2 ? (
          <Button
            variant="contained"
            onClick={() => formik.handleSubmit()}
          >
            Submit
          </Button>
        ) : (
          <Button
            variant="contained"
            onClick={handleNext}
          >
            Next
          </Button>
        )}
      </Box>
    </Box>
  );
};

export default WizardForm;
```

### After (useAdvancedForm)

```typescript
import React, { useState } from 'react';
import { TextField, Button, Box, Stepper, Step, StepLabel, Grid, Alert } from '@mui/material';
import { useAdvancedForm } from '@/hooks/enterprise';
import { useNotifications } from 'contexts/NotificationContext';
import { createValidationRules } from '@/utils/validationHelpers';
import type { IValidationRule } from '@/types/common';

interface WizardFormData {
  firstName: string;
  lastName: string;
  email: string;
  address: string;
  city: string;
  zipCode: string;
  cardNumber: string;
  expiryDate: string;
  cvv: string;
}

const validationRules: Partial<Record<keyof WizardFormData, IValidationRule[]>> = {
  firstName: [
    { type: 'required', message: 'First name is required' },
    { type: 'name', message: 'Please enter a valid first name' },
  ],
  lastName: [
    { type: 'required', message: 'Last name is required' },
    { type: 'name', message: 'Please enter a valid last name' },
  ],
  email: [
    { type: 'required', message: 'Email is required' },
    { type: 'emailStrict', message: 'Invalid email' },
  ],
  address: [
    { type: 'required', message: 'Address is required' },
    { type: 'minLength', value: 5, message: 'Address must be at least 5 characters' },
  ],
  city: [
    { type: 'required', message: 'City is required' },
    { type: 'name', message: 'Please enter a valid city name' },
  ],
  zipCode: [
    { type: 'required', message: 'ZIP code is required' },
    { type: 'zipUS', message: 'Please enter a valid ZIP code' },
  ],
  cardNumber: [
    { type: 'required', message: 'Card number is required' },
    { type: 'creditCard', message: 'Please enter a valid card number' },
  ],
  expiryDate: [
    { type: 'required', message: 'Expiry date is required' },
    { type: 'expiryDate', message: 'Please enter expiry date in MM/YY format' },
  ],
  cvv: [
    { type: 'required', message: 'CVV is required' },
    { type: 'cvv', message: 'CVV must be 3-4 digits' },
  ],
};

const WizardForm = () => {
  const [activeStep, setActiveStep] = useState(0);
  const notifications = useNotifications();
  
  const form = useAdvancedForm<WizardFormData>({
    initialValues: {
      firstName: '',
      lastName: '',
      email: '',
      address: '',
      city: '',
      zipCode: '',
      cardNumber: '',
      expiryDate: '',
      cvv: '',
    },
    validationRules,
    validateOnChange: true,
    validateOnBlur: true,
    onSubmit: async (values) => {
      try {
        // Simulate API call
        await new Promise(resolve => setTimeout(resolve, 1000));
        console.log(values);
        
        notifications.showNotification({
          message: 'Form submitted successfully!',
          variant: 'success',
          alert: { color: 'success', variant: 'filled' },
          close: true,
        });
      } catch (error) {
        notifications.showNotification({
          message: 'Submission failed. Please try again.',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      }
    },
  });

  const handleNext = () => {
    setActiveStep(activeStep + 1);
  };

  const handleBack = () => {
    setActiveStep(activeStep - 1);
  };

  const renderStepContent = (step: number) => {
    switch (step) {
      case 0:
        return (
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="First Name"
                name="firstName"
                value={form.values.firstName}
                onChange={form.handleChange('firstName')}
                onBlur={form.handleBlur('firstName')}
                error={Boolean(form.touched.firstName && form.errors.firstName)}
                helperText={form.touched.firstName && form.errors.firstName}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Last Name"
                name="lastName"
                value={form.values.lastName}
                onChange={form.handleChange('lastName')}
                onBlur={form.handleBlur('lastName')}
                error={Boolean(form.touched.lastName && form.errors.lastName)}
                helperText={form.touched.lastName && form.errors.lastName}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Email"
                name="email"
                value={form.values.email}
                onChange={form.handleChange('email')}
                onBlur={form.handleBlur('email')}
                error={Boolean(form.touched.email && form.errors.email)}
                helperText={form.touched.email && form.errors.email}
              />
            </Grid>
          </Grid>
        );
      case 1:
        return (
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Address"
                name="address"
                value={form.values.address}
                onChange={form.handleChange('address')}
                onBlur={form.handleBlur('address')}
                error={Boolean(form.touched.address && form.errors.address)}
                helperText={form.touched.address && form.errors.address}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="City"
                name="city"
                value={form.values.city}
                onChange={form.handleChange('city')}
                onBlur={form.handleBlur('city')}
                error={Boolean(form.touched.city && form.errors.city)}
                helperText={form.touched.city && form.errors.city}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="ZIP Code"
                name="zipCode"
                value={form.values.zipCode}
                onChange={form.handleChange('zipCode')}
                onBlur={form.handleBlur('zipCode')}
                error={Boolean(form.touched.zipCode && form.errors.zipCode)}
                helperText={form.touched.zipCode && form.errors.zipCode}
              />
            </Grid>
          </Grid>
        );
      case 2:
        return (
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Card Number"
                name="cardNumber"
                value={form.values.cardNumber}
                onChange={form.handleChange('cardNumber')}
                onBlur={form.handleBlur('cardNumber')}
                error={Boolean(form.touched.cardNumber && form.errors.cardNumber)}
                helperText={form.touched.cardNumber && form.errors.cardNumber}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Expiry Date"
                name="expiryDate"
                value={form.values.expiryDate}
                onChange={form.handleChange('expiryDate')}
                onBlur={form.handleBlur('expiryDate')}
                error={Boolean(form.touched.expiryDate && form.errors.expiryDate)}
                helperText={form.touched.expiryDate && form.errors.expiryDate}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="CVV"
                name="cvv"
                value={form.values.cvv}
                onChange={form.handleChange('cvv')}
                onBlur={form.handleBlur('cvv')}
                error={Boolean(form.touched.cvv && form.errors.cvv)}
                helperText={form.touched.cvv && form.errors.cvv}
              />
            </Grid>
          </Grid>
        );
      default:
        return null;
    }
  };

  return (
    <Box>
      <Stepper activeStep={activeStep}>
        <Step><StepLabel>Personal Info</StepLabel></Step>
        <Step><StepLabel>Address</StepLabel></Step>
        <Step><StepLabel>Payment</StepLabel></Step>
      </Stepper>
      
      <Box sx={{ mt: 3 }}>
        {renderStepContent(activeStep)}
      </Box>
      
      {/* Form status feedback */}
      {Object.keys(form.errors).length > 0 && Object.keys(form.touched).length > 0 && (
        <Alert severity="error" sx={{ mt: 2 }}>
          Please fix the errors above before submitting
        </Alert>
      )}
      
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 3 }}>
        <Button
          disabled={activeStep === 0}
          onClick={handleBack}
        >
          Back
        </Button>
        {activeStep === 2 ? (
          <Button
            variant="contained"
            onClick={() => form.handleSubmit()}
            disabled={form.isSubmitting || !form.isValid}
          >
            {form.isSubmitting ? 'Submitting...' : 'Submit'}
          </Button>
        ) : (
          <Button
            variant="contained"
            onClick={handleNext}
          >
            Next
          </Button>
        )}
      </Box>
    </Box>
  );
};

export default WizardForm;
```

## Migration Checklist

### Pre-Migration
- [ ] Identify all Formik-based forms in the codebase
- [ ] Document current form functionality and validation
- [ ] Plan migration order (start with simple forms)
- [ ] Set up testing environment

### During Migration
- [ ] Replace Formik imports with useAdvancedForm imports
- [ ] Define TypeScript interfaces for form data
- [ ] Convert Yup schemas to validation rules
- [ ] Update field bindings (add onBlur handlers)
- [ ] Add loading states and error handling
- [ ] Test form validation and submission
- [ ] Add success/error notifications

### Post-Migration
- [ ] Remove Formik and Yup dependencies
- [ ] Update tests to use new form patterns
- [ ] Document any custom validation logic
- [ ] Verify all forms work correctly
- [ ] Performance test form interactions

### Common Pitfalls to Avoid

1. **Forgetting onBlur handlers** - Always add onBlur for proper validation
2. **Incorrect error binding** - Use `Boolean(form.touched.field && form.errors.field)`
3. **Missing form element** - Wrap submit button in form element
4. **Type safety issues** - Define proper interfaces for form data
5. **Validation rule order** - Put required validation first
6. **Loading state handling** - Always handle isSubmitting state
7. **Error notifications** - Add proper success/error feedback

## Testing Migration

### Unit Tests
```typescript
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MyForm } from './MyForm';

test('form validation works correctly', async () => {
  render(<MyForm />);
  
  const submitButton = screen.getByRole('button', { name: /submit/i });
  fireEvent.click(submitButton);
  
  await waitFor(() => {
    expect(screen.getByText('Email is required')).toBeInTheDocument();
  });
});

test('form submission works correctly', async () => {
  render(<MyForm />);
  
  const emailInput = screen.getByLabelText(/email/i);
  const passwordInput = screen.getByLabelText(/password/i);
  const submitButton = screen.getByRole('button', { name: /submit/i });
  
  fireEvent.change(emailInput, { target: { value: 'test@example.com' } });
  fireEvent.change(passwordInput, { target: { value: 'password123' } });
  fireEvent.click(submitButton);
  
  await waitFor(() => {
    expect(screen.getByText('Login successful!')).toBeInTheDocument();
  });
});
```

### Integration Tests
```typescript
test('form integrates with API correctly', async () => {
  const mockSubmit = jest.fn();
  render(<MyForm onSubmit={mockSubmit} />);
  
  // Fill form and submit
  // Verify API call was made with correct data
  expect(mockSubmit).toHaveBeenCalledWith({
    email: 'test@example.com',
    password: 'password123',
  });
});
```

This migration guide provides comprehensive examples and step-by-step instructions for converting Formik-based forms to the modern useAdvancedForm pattern while maintaining all functionality and improving type safety and user experience.