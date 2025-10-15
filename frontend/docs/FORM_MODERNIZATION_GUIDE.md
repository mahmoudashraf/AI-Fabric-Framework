# Enterprise Form Modernization Guide

## Overview

This guide documents the comprehensive form modernization implemented in Phase 2, which transforms all form components from legacy Formik-based patterns to modern enterprise-grade patterns using the `useAdvancedForm` hook.

## Table of Contents

1. [Quick Start](#quick-start)
2. [Core Concepts](#core-concepts)
3. [useAdvancedForm Hook](#useadvancedform-hook)
4. [Validation System](#validation-system)
5. [Form Patterns](#form-patterns)
6. [Component Examples](#component-examples)
7. [Best Practices](#best-practices)
8. [Migration Guide](#migration-guide)
9. [Troubleshooting](#troubleshooting)

## Quick Start

### Basic Form Setup

```typescript
import { useAdvancedForm } from '@/hooks/enterprise';
import { useNotifications } from 'contexts/NotificationContext';
import type { IValidationRule } from '@/types/common';

// 1. Define form data interface
interface MyFormData {
  firstName: string;
  lastName: string;
  email: string;
}

// 2. Define validation rules
const validationRules: Partial<Record<keyof MyFormData, IValidationRule[]>> = {
  firstName: [
    { type: 'required', message: 'First name is required' },
    { type: 'minLength', value: 2, message: 'First name must be at least 2 characters' },
  ],
  lastName: [
    { type: 'required', message: 'Last name is required' },
    { type: 'minLength', value: 2, message: 'Last name must be at least 2 characters' },
  ],
  email: [
    { type: 'required', message: 'Email is required' },
    { type: 'email', message: 'Please enter a valid email address' },
  ],
};

// 3. Use the hook in your component
const MyForm = () => {
  const notifications = useNotifications();
  
  const form = useAdvancedForm<MyFormData>({
    initialValues: {
      firstName: '',
      lastName: '',
      email: '',
    },
    validationRules,
    validateOnChange: true,
    validateOnBlur: true,
    onSubmit: async (values) => {
      try {
        // Your API call here
        await submitForm(values);
        notifications.showNotification({
          message: 'Form submitted successfully!',
          variant: 'success',
          alert: { color: 'success', variant: 'filled' },
          close: true,
        });
      } catch (error) {
        notifications.showNotification({
          message: 'Failed to submit form. Please try again.',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      }
    },
  });

  return (
    <form onSubmit={form.handleSubmit()}>
      <TextField
        name="firstName"
        label="First Name"
        value={form.values.firstName}
        onChange={form.handleChange('firstName')}
        onBlur={form.handleBlur('firstName')}
        error={Boolean(form.touched.firstName && form.errors.firstName)}
        helperText={form.touched.firstName && form.errors.firstName}
        fullWidth
      />
      {/* More fields... */}
      <Button
        type="submit"
        disabled={form.isSubmitting || !form.isValid}
        variant="contained"
      >
        {form.isSubmitting ? 'Submitting...' : 'Submit'}
      </Button>
    </form>
  );
};
```

## Core Concepts

### Enterprise Form Patterns

The modernized form system follows these core enterprise patterns:

1. **Type Safety** - Full TypeScript support with generic form interfaces
2. **Validation** - Comprehensive validation system with 20+ rule types
3. **State Management** - Advanced form state with loading, error, and dirty tracking
4. **User Experience** - Real-time validation, loading states, and notifications
5. **Developer Experience** - Consistent patterns and reusable helpers

### Key Benefits

- ✅ **Type Safety** - Compile-time error prevention
- ✅ **Better UX** - Real-time validation and loading states
- ✅ **Consistency** - Uniform patterns across all forms
- ✅ **Maintainability** - Reusable validation helpers
- ✅ **Performance** - Optimized rendering and state management

## useAdvancedForm Hook

### Interface

```typescript
interface UseAdvancedFormOptions<TForm> {
  initialValues: TForm;
  validationRules?: Partial<Record<keyof TForm, IValidationRule[]>>;
  validateOnChange?: boolean;
  validateOnBlur?: boolean;
  onSubmit: (values: TForm) => Promise<void> | void;
}

interface UseAdvancedFormResult<TForm> {
  values: TForm;
  errors: Partial<Record<keyof TForm, string>>;
  touched: Partial<Record<keyof TForm, boolean>>;
  isDirty: boolean;
  isValid: boolean;
  isSubmitting: boolean;
  setValue: (field: keyof TForm, value: any) => void;
  handleChange: (field: keyof TForm) => (event: any) => void;
  handleBlur: (field: keyof TForm) => (event: any) => void;
  handleSubmit: () => (event: React.FormEvent) => void;
  resetForm: () => void;
}
```

### Usage Examples

#### Basic Form
```typescript
const form = useAdvancedForm<MyFormData>({
  initialValues: { name: '', email: '' },
  validationRules: {
    name: [{ type: 'required', message: 'Name is required' }],
    email: [{ type: 'email', message: 'Invalid email' }],
  },
  onSubmit: async (values) => {
    await submitData(values);
  },
});
```

#### Advanced Form with Custom Validation
```typescript
const form = useAdvancedForm<ComplexFormData>({
  initialValues: { password: '', confirmPassword: '' },
  validationRules: {
    password: [
      { type: 'required', message: 'Password is required' },
      { type: 'passwordStrong', message: 'Password must be strong' },
    ],
    confirmPassword: [
      { type: 'required', message: 'Please confirm password' },
      { 
        type: 'custom', 
        message: 'Passwords must match',
        validator: (value, formValues) => value === formValues.password
      },
    ],
  },
  validateOnChange: true,
  validateOnBlur: true,
  onSubmit: async (values) => {
    await registerUser(values);
  },
});
```

## Validation System

### Validation Rule Types

The system supports 20+ validation rule types:

#### Basic Rules
```typescript
// Required field
{ type: 'required', message: 'This field is required' }

// Length validation
{ type: 'minLength', value: 5, message: 'Must be at least 5 characters' }
{ type: 'maxLength', value: 100, message: 'Must be no more than 100 characters' }

// Numeric validation
{ type: 'min', value: 0, message: 'Must be at least 0' }
{ type: 'max', value: 100, message: 'Must be no more than 100' }
```

#### Email Validation
```typescript
// Standard email
{ type: 'email', message: 'Please enter a valid email address' }

// Strict email (more restrictive)
{ type: 'emailStrict', message: 'Please enter a valid email address' }
```

#### Phone Validation
```typescript
// US phone number
{ type: 'phoneUS', message: 'Please enter a valid US phone number' }

// International phone number
{ type: 'phone', message: 'Please enter a valid phone number' }
```

#### Credit Card Validation
```typescript
// Credit card number
{ type: 'creditCard', message: 'Please enter a valid credit card number' }

// CVV
{ type: 'cvv', message: 'CVV must be 3-4 digits' }

// Expiry date
{ type: 'expiryDate', message: 'Please enter expiry date in MM/YY format' }
```

#### Address Validation
```typescript
// US ZIP code
{ type: 'zipUS', message: 'Please enter a valid US zip code' }

// Canadian postal code
{ type: 'zipCanada', message: 'Please enter a valid Canadian postal code' }
```

#### Password Validation
```typescript
// Strong password (uppercase, lowercase, number, special char)
{ type: 'passwordStrong', message: 'Password must contain uppercase, lowercase, number and special character' }

// Medium password (uppercase, lowercase, number)
{ type: 'passwordMedium', message: 'Password must contain uppercase, lowercase and number' }
```

#### Other Validation Types
```typescript
// Name validation
{ type: 'name', message: 'Please enter a valid name' }

// Username validation
{ type: 'username', message: 'Username can only contain letters, numbers, hyphens and underscores' }

// URL validation
{ type: 'url', message: 'Please enter a valid URL' }

// Date validation
{ type: 'date', message: 'Please enter a valid date' }

// Currency validation
{ type: 'currency', message: 'Please enter a valid currency amount' }

// Percentage validation
{ type: 'percentage', message: 'Please enter a valid percentage (0-100)' }

// Custom validation
{ 
  type: 'custom', 
  message: 'Custom validation failed',
  validator: (value, formValues) => {
    // Your custom validation logic
    return value.length > 0;
  }
}

// Pattern validation
{ 
  type: 'pattern', 
  value: /^[A-Za-z]+$/, 
  message: 'Only letters are allowed' 
}
```

### Validation Helpers

Use the validation helper functions for easier rule creation:

```typescript
import { createValidationRules, commonValidationSets } from '@/utils/validationHelpers';

// Using helper functions
const validationRules = {
  email: [createValidationRules.email()],
  password: [createValidationRules.passwordStrong()],
  phone: [createValidationRules.phoneUS()],
};

// Using pre-built validation sets
const userProfileRules = commonValidationSets.userProfile;
const addressRules = commonValidationSets.address;
const paymentRules = commonValidationSets.payment;
```

## Form Patterns

### 1. Basic Text Form

```typescript
interface BasicFormData {
  name: string;
  email: string;
  message: string;
}

const BasicForm = () => {
  const notifications = useNotifications();
  
  const form = useAdvancedForm<BasicFormData>({
    initialValues: { name: '', email: '', message: '' },
    validationRules: {
      name: [createValidationRules.required(), createValidationRules.name()],
      email: [createValidationRules.required(), createValidationRules.email()],
      message: [createValidationRules.required(), createValidationRules.minLength(10)],
    },
    onSubmit: async (values) => {
      await submitContactForm(values);
      notifications.showNotification({
        message: 'Message sent successfully!',
        variant: 'success',
        alert: { color: 'success', variant: 'filled' },
        close: true,
      });
    },
  });

  return (
    <form onSubmit={form.handleSubmit()}>
      <Grid container spacing={3}>
        <Grid size={12}>
          <TextField
            name="name"
            label="Full Name"
            value={form.values.name}
            onChange={form.handleChange('name')}
            onBlur={form.handleBlur('name')}
            error={Boolean(form.touched.name && form.errors.name)}
            helperText={form.touched.name && form.errors.name}
            fullWidth
            required
          />
        </Grid>
        <Grid size={12}>
          <TextField
            name="email"
            label="Email Address"
            type="email"
            value={form.values.email}
            onChange={form.handleChange('email')}
            onBlur={form.handleBlur('email')}
            error={Boolean(form.touched.email && form.errors.email)}
            helperText={form.touched.email && form.errors.email}
            fullWidth
            required
          />
        </Grid>
        <Grid size={12}>
          <TextField
            name="message"
            label="Message"
            multiline
            rows={4}
            value={form.values.message}
            onChange={form.handleChange('message')}
            onBlur={form.handleBlur('message')}
            error={Boolean(form.touched.message && form.errors.message)}
            helperText={form.touched.message && form.errors.message}
            fullWidth
            required
          />
        </Grid>
        <Grid size={12}>
          <Button
            type="submit"
            variant="contained"
            disabled={form.isSubmitting || !form.isValid}
            fullWidth
          >
            {form.isSubmitting ? 'Sending...' : 'Send Message'}
          </Button>
        </Grid>
      </Grid>
    </form>
  );
};
```

### 2. Authentication Form

```typescript
interface LoginFormData {
  email: string;
  password: string;
  rememberMe: boolean;
}

const LoginForm = () => {
  const { login } = useAuth();
  const notifications = useNotifications();
  
  const form = useAdvancedForm<LoginFormData>({
    initialValues: { email: '', password: '', rememberMe: false },
    validationRules: {
      email: [createValidationRules.required(), createValidationRules.email()],
      password: [createValidationRules.required(), createValidationRules.minLength(8)],
    },
    onSubmit: async (values) => {
      try {
        await login(values.email, values.password);
        notifications.showNotification({
          message: 'Login successful!',
          variant: 'success',
          alert: { color: 'success', variant: 'filled' },
          close: true,
        });
      } catch (error) {
        notifications.showNotification({
          message: 'Login failed. Please check your credentials.',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      }
    },
  });

  return (
    <form onSubmit={form.handleSubmit()}>
      <TextField
        name="email"
        label="Email Address"
        type="email"
        value={form.values.email}
        onChange={form.handleChange('email')}
        onBlur={form.handleBlur('email')}
        error={Boolean(form.touched.email && form.errors.email)}
        helperText={form.touched.email && form.errors.email}
        fullWidth
        required
      />
      <TextField
        name="password"
        label="Password"
        type="password"
        value={form.values.password}
        onChange={form.handleChange('password')}
        onBlur={form.handleBlur('password')}
        error={Boolean(form.touched.password && form.errors.password)}
        helperText={form.touched.password && form.errors.password}
        fullWidth
        required
      />
      <FormControlLabel
        control={
          <Checkbox
            checked={form.values.rememberMe}
            onChange={(event) => form.setValue('rememberMe', event.target.checked)}
          />
        }
        label="Remember me"
      />
      <Button
        type="submit"
        variant="contained"
        disabled={form.isSubmitting || !form.isValid}
        fullWidth
      >
        {form.isSubmitting ? 'Signing in...' : 'Sign In'}
      </Button>
    </form>
  );
};
```

### 3. Multi-Step Wizard Form

```typescript
interface WizardFormData {
  // Step 1: Personal Info
  firstName: string;
  lastName: string;
  email: string;
  
  // Step 2: Address
  address1: string;
  city: string;
  zip: string;
  country: string;
  
  // Step 3: Payment
  cardName: string;
  cardNumber: string;
  expDate: string;
  cvv: string;
}

const WizardForm = () => {
  const [currentStep, setCurrentStep] = useState(0);
  const notifications = useNotifications();
  
  const form = useAdvancedForm<WizardFormData>({
    initialValues: {
      firstName: '', lastName: '', email: '',
      address1: '', city: '', zip: '', country: '',
      cardName: '', cardNumber: '', expDate: '', cvv: '',
    },
    validationRules: {
      firstName: [createValidationRules.required(), createValidationRules.name()],
      lastName: [createValidationRules.required(), createValidationRules.name()],
      email: [createValidationRules.required(), createValidationRules.email()],
      address1: [createValidationRules.required(), createValidationRules.minLength(5)],
      city: [createValidationRules.required(), createValidationRules.name()],
      zip: [createValidationRules.required(), createValidationRules.zipUS()],
      country: [createValidationRules.required(), createValidationRules.name()],
      cardName: [createValidationRules.required(), createValidationRules.name()],
      cardNumber: [createValidationRules.required(), createValidationRules.creditCard()],
      expDate: [createValidationRules.required(), createValidationRules.expiryDate()],
      cvv: [createValidationRules.required(), createValidationRules.cvv()],
    },
    onSubmit: async (values) => {
      try {
        await submitWizardForm(values);
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
    if (currentStep < 2) {
      setCurrentStep(currentStep + 1);
    }
  };

  const handleBack = () => {
    if (currentStep > 0) {
      setCurrentStep(currentStep - 1);
    }
  };

  const renderStep = () => {
    switch (currentStep) {
      case 0:
        return (
          <Grid container spacing={3}>
            <Grid size={12}>
              <TextField
                name="firstName"
                label="First Name"
                value={form.values.firstName}
                onChange={form.handleChange('firstName')}
                onBlur={form.handleBlur('firstName')}
                error={Boolean(form.touched.firstName && form.errors.firstName)}
                helperText={form.touched.firstName && form.errors.firstName}
                fullWidth
                required
              />
            </Grid>
            {/* More fields... */}
          </Grid>
        );
      case 1:
        return (
          <Grid container spacing={3}>
            <Grid size={12}>
              <TextField
                name="address1"
                label="Address Line 1"
                value={form.values.address1}
                onChange={form.handleChange('address1')}
                onBlur={form.handleBlur('address1')}
                error={Boolean(form.touched.address1 && form.errors.address1)}
                helperText={form.touched.address1 && form.errors.address1}
                fullWidth
                required
              />
            </Grid>
            {/* More fields... */}
          </Grid>
        );
      case 2:
        return (
          <Grid container spacing={3}>
            <Grid size={12}>
              <TextField
                name="cardName"
                label="Name on Card"
                value={form.values.cardName}
                onChange={form.handleChange('cardName')}
                onBlur={form.handleBlur('cardName')}
                error={Boolean(form.touched.cardName && form.errors.cardName)}
                helperText={form.touched.cardName && form.errors.cardName}
                fullWidth
                required
              />
            </Grid>
            {/* More fields... */}
          </Grid>
        );
      default:
        return null;
    }
  };

  return (
    <form onSubmit={form.handleSubmit()}>
      <Stepper activeStep={currentStep}>
        <Step><StepLabel>Personal Info</StepLabel></Step>
        <Step><StepLabel>Address</StepLabel></Step>
        <Step><StepLabel>Payment</StepLabel></Step>
      </Stepper>
      
      {renderStep()}
      
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 3 }}>
        <Button
          onClick={handleBack}
          disabled={currentStep === 0}
        >
          Back
        </Button>
        {currentStep === 2 ? (
          <Button
            type="submit"
            variant="contained"
            disabled={form.isSubmitting || !form.isValid}
          >
            {form.isSubmitting ? 'Submitting...' : 'Submit'}
          </Button>
        ) : (
          <Button
            onClick={handleNext}
            variant="contained"
          >
            Next
          </Button>
        )}
      </Box>
    </form>
  );
};
```

### 4. Form with File Upload

```typescript
interface FileUploadFormData {
  title: string;
  description: string;
  file: File | null;
}

const FileUploadForm = () => {
  const notifications = useNotifications();
  
  const form = useAdvancedForm<FileUploadFormData>({
    initialValues: { title: '', description: '', file: null },
    validationRules: {
      title: [createValidationRules.required(), createValidationRules.minLength(3)],
      description: [createValidationRules.required(), createValidationRules.minLength(10)],
      file: [createValidationRules.required()],
    },
    onSubmit: async (values) => {
      const formData = new FormData();
      formData.append('title', values.title);
      formData.append('description', values.description);
      if (values.file) {
        formData.append('file', values.file);
      }
      
      await uploadFile(formData);
      notifications.showNotification({
        message: 'File uploaded successfully!',
        variant: 'success',
        alert: { color: 'success', variant: 'filled' },
        close: true,
      });
    },
  });

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] || null;
    form.setValue('file', file);
  };

  return (
    <form onSubmit={form.handleSubmit()}>
      <TextField
        name="title"
        label="Title"
        value={form.values.title}
        onChange={form.handleChange('title')}
        onBlur={form.handleBlur('title')}
        error={Boolean(form.touched.title && form.errors.title)}
        helperText={form.touched.title && form.errors.title}
        fullWidth
        required
      />
      
      <TextField
        name="description"
        label="Description"
        multiline
        rows={4}
        value={form.values.description}
        onChange={form.handleChange('description')}
        onBlur={form.handleBlur('description')}
        error={Boolean(form.touched.description && form.errors.description)}
        helperText={form.touched.description && form.errors.description}
        fullWidth
        required
      />
      
      <input
        type="file"
        onChange={handleFileChange}
        style={{ margin: '16px 0' }}
      />
      
      <Button
        type="submit"
        variant="contained"
        disabled={form.isSubmitting || !form.isValid}
        fullWidth
      >
        {form.isSubmitting ? 'Uploading...' : 'Upload File'}
      </Button>
    </form>
  );
};
```

## Best Practices

### 1. Form Interface Design

```typescript
// ✅ Good: Clear, descriptive interface
interface UserRegistrationData {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  confirmPassword: string;
  agreeToTerms: boolean;
}

// ❌ Bad: Vague or unclear naming
interface FormData {
  f1: string;
  f2: string;
  f3: string;
}
```

### 2. Validation Rules

```typescript
// ✅ Good: Comprehensive validation
const validationRules: Partial<Record<keyof UserRegistrationData, IValidationRule[]>> = {
  firstName: [
    { type: 'required', message: 'First name is required' },
    { type: 'minLength', value: 2, message: 'First name must be at least 2 characters' },
    { type: 'name', message: 'First name can only contain letters' },
  ],
  email: [
    { type: 'required', message: 'Email is required' },
    { type: 'emailStrict', message: 'Please enter a valid email address' },
  ],
  password: [
    { type: 'required', message: 'Password is required' },
    { type: 'passwordStrong', message: 'Password must be strong' },
  ],
  confirmPassword: [
    { type: 'required', message: 'Please confirm your password' },
    { 
      type: 'custom', 
      message: 'Passwords must match',
      validator: (value, formValues) => value === formValues.password
    },
  ],
};

// ❌ Bad: Minimal or no validation
const validationRules = {
  firstName: [{ type: 'required', message: 'Required' }],
};
```

### 3. Error Handling

```typescript
// ✅ Good: Comprehensive error handling
const form = useAdvancedForm<MyFormData>({
  // ... other options
  onSubmit: async (values) => {
    try {
      await submitData(values);
      notifications.showNotification({
        message: 'Data saved successfully!',
        variant: 'success',
        alert: { color: 'success', variant: 'filled' },
        close: true,
      });
    } catch (error) {
      console.error('Form submission error:', error);
      notifications.showNotification({
        message: 'Failed to save data. Please try again.',
        variant: 'error',
        alert: { color: 'error', variant: 'filled' },
        close: true,
      });
    }
  },
});

// ❌ Bad: No error handling
const form = useAdvancedForm<MyFormData>({
  // ... other options
  onSubmit: async (values) => {
    await submitData(values); // What if this fails?
  },
});
```

### 4. Form State Management

```typescript
// ✅ Good: Proper form state usage
<Button
  type="submit"
  variant="contained"
  disabled={form.isSubmitting || !form.isValid}
  fullWidth
>
  {form.isSubmitting ? 'Submitting...' : 'Submit'}
</Button>

// ❌ Bad: Ignoring form state
<Button
  type="submit"
  variant="contained"
  fullWidth
>
  Submit
</Button>
```

### 5. Field Binding

```typescript
// ✅ Good: Complete field binding
<TextField
  name="email"
  label="Email Address"
  type="email"
  value={form.values.email}
  onChange={form.handleChange('email')}
  onBlur={form.handleBlur('email')}
  error={Boolean(form.touched.email && form.errors.email)}
  helperText={form.touched.email && form.errors.email}
  fullWidth
  required
/>

// ❌ Bad: Incomplete field binding
<TextField
  name="email"
  label="Email Address"
  value={form.values.email}
  onChange={form.handleChange('email')}
  fullWidth
/>
```

## Migration Guide

### From Formik to useAdvancedForm

#### Before (Formik)
```typescript
import { useFormik } from 'formik';
import * as yup from 'yup';

const validationSchema = yup.object({
  email: yup.string().email('Invalid email').required('Email is required'),
  password: yup.string().min(8, 'Password must be at least 8 characters').required('Password is required'),
});

const MyForm = () => {
  const formik = useFormik({
    initialValues: { email: '', password: '' },
    validationSchema,
    onSubmit: (values) => {
      console.log(values);
    },
  });

  return (
    <form onSubmit={formik.handleSubmit}>
      <TextField
        name="email"
        value={formik.values.email}
        onChange={formik.handleChange}
        error={formik.touched.email && Boolean(formik.errors.email)}
        helperText={formik.touched.email && formik.errors.email}
      />
      <TextField
        name="password"
        type="password"
        value={formik.values.password}
        onChange={formik.handleChange}
        error={formik.touched.password && Boolean(formik.errors.password)}
        helperText={formik.touched.password && formik.errors.password}
      />
      <Button type="submit">Submit</Button>
    </form>
  );
};
```

#### After (useAdvancedForm)
```typescript
import { useAdvancedForm } from '@/hooks/enterprise';
import { useNotifications } from 'contexts/NotificationContext';
import type { IValidationRule } from '@/types/common';

interface MyFormData {
  email: string;
  password: string;
}

const validationRules: Partial<Record<keyof MyFormData, IValidationRule[]>> = {
  email: [
    { type: 'email', message: 'Invalid email' },
    { type: 'required', message: 'Email is required' },
  ],
  password: [
    { type: 'minLength', value: 8, message: 'Password must be at least 8 characters' },
    { type: 'required', message: 'Password is required' },
  ],
};

const MyForm = () => {
  const notifications = useNotifications();
  
  const form = useAdvancedForm<MyFormData>({
    initialValues: { email: '', password: '' },
    validationRules,
    onSubmit: async (values) => {
      try {
        await submitData(values);
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

  return (
    <form onSubmit={form.handleSubmit()}>
      <TextField
        name="email"
        value={form.values.email}
        onChange={form.handleChange('email')}
        onBlur={form.handleBlur('email')}
        error={Boolean(form.touched.email && form.errors.email)}
        helperText={form.touched.email && form.errors.email}
      />
      <TextField
        name="password"
        type="password"
        value={form.values.password}
        onChange={form.handleChange('password')}
        onBlur={form.handleBlur('password')}
        error={Boolean(form.touched.password && form.errors.password)}
        helperText={form.touched.password && form.errors.password}
      />
      <Button 
        type="submit" 
        disabled={form.isSubmitting || !form.isValid}
      >
        {form.isSubmitting ? 'Submitting...' : 'Submit'}
      </Button>
    </form>
  );
};
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
   import type { IValidationRule } from '@/types/common';
   ```

2. **Define form interface**
   ```typescript
   interface MyFormData {
     // Define your form fields here
   }
   ```

3. **Convert validation schema**
   ```typescript
   // From Yup schema
   const validationSchema = yup.object({
     email: yup.string().email().required(),
   });
   
   // To validation rules
   const validationRules: Partial<Record<keyof MyFormData, IValidationRule[]>> = {
     email: [
       { type: 'email', message: 'Invalid email' },
       { type: 'required', message: 'Email is required' },
     ],
   };
   ```

4. **Replace useFormik with useAdvancedForm**
   ```typescript
   // Update hook usage and add proper error handling
   ```

5. **Update field bindings**
   ```typescript
   // Add onBlur and update error handling
   ```

6. **Add notifications**
   ```typescript
   // Add success/error notifications
   ```

## Troubleshooting

### Common Issues

#### 1. TypeScript Errors

**Problem**: Type errors with form interfaces
```typescript
// Error: Property 'email' does not exist on type 'never'
const form = useAdvancedForm<{}>({
  initialValues: { email: '' }, // Error here
});
```

**Solution**: Define proper interface
```typescript
interface MyFormData {
  email: string;
}

const form = useAdvancedForm<MyFormData>({
  initialValues: { email: '' }, // Now works
});
```

#### 2. Validation Not Working

**Problem**: Validation rules not being applied
```typescript
// Validation not working
const validationRules = {
  email: [{ type: 'email', message: 'Invalid email' }], // Missing required
};
```

**Solution**: Add required validation
```typescript
const validationRules = {
  email: [
    { type: 'required', message: 'Email is required' },
    { type: 'email', message: 'Invalid email' }
  ],
};
```

#### 3. Form Not Submitting

**Problem**: Form submission not working
```typescript
// Missing form element or onSubmit handler
<Button onClick={handleSubmit}>Submit</Button>
```

**Solution**: Use proper form structure
```typescript
<form onSubmit={form.handleSubmit()}>
  <Button type="submit">Submit</Button>
</form>
```

#### 4. Error Messages Not Showing

**Problem**: Error messages not displaying
```typescript
// Missing error binding
<TextField
  value={form.values.email}
  onChange={form.handleChange('email')}
  // Missing error and helperText props
/>
```

**Solution**: Add error binding
```typescript
<TextField
  value={form.values.email}
  onChange={form.handleChange('email')}
  onBlur={form.handleBlur('email')}
  error={Boolean(form.touched.email && form.errors.email)}
  helperText={form.touched.email && form.errors.email}
/>
```

### Performance Tips

1. **Use validateOnChange and validateOnBlur wisely**
   ```typescript
   // For real-time validation
   validateOnChange: true,
   validateOnBlur: true,
   
   // For better performance (validate only on submit)
   validateOnChange: false,
   validateOnBlur: false,
   ```

2. **Optimize validation rules**
   ```typescript
   // Use specific validation types instead of custom when possible
   { type: 'email', message: 'Invalid email' } // ✅ Fast
   
   { 
     type: 'custom', 
     validator: (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value),
     message: 'Invalid email' 
   } // ❌ Slower
   ```

3. **Memoize validation rules**
   ```typescript
   // Define outside component to prevent recreation
   const validationRules = {
     email: [createValidationRules.email()],
   };
   
   const MyForm = () => {
     // Use validationRules here
   };
   ```

## Conclusion

The enterprise form modernization provides a robust, type-safe, and user-friendly form system that significantly improves both developer and user experiences. By following the patterns and best practices outlined in this guide, you can create maintainable, performant, and accessible forms that meet enterprise-grade standards.

For additional support or questions, refer to the component examples in the codebase or consult the TypeScript definitions for detailed API documentation.