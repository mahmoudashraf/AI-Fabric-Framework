# Form Modernization API Reference

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

### Parameters

#### `initialValues: TForm`
The initial values for the form fields.

```typescript
const form = useAdvancedForm<MyFormData>({
  initialValues: {
    firstName: '',
    lastName: '',
    email: '',
  },
  // ... other options
});
```

#### `validationRules?: Partial<Record<keyof TForm, IValidationRule[]>>`
Validation rules for form fields. Optional.

```typescript
const validationRules: Partial<Record<keyof MyFormData, IValidationRule[]>> = {
  firstName: [
    { type: 'required', message: 'First name is required' },
    { type: 'minLength', value: 2, message: 'Must be at least 2 characters' },
  ],
  email: [
    { type: 'required', message: 'Email is required' },
    { type: 'email', message: 'Invalid email format' },
  ],
};
```

#### `validateOnChange?: boolean`
Whether to validate fields on change. Default: `false`

#### `validateOnBlur?: boolean`
Whether to validate fields on blur. Default: `false`

#### `onSubmit: (values: TForm) => Promise<void> | void`
Function called when form is submitted.

```typescript
const form = useAdvancedForm<MyFormData>({
  // ... other options
  onSubmit: async (values) => {
    try {
      await submitData(values);
      // Handle success
    } catch (error) {
      // Handle error
    }
  },
});
```

### Return Values

#### `values: TForm`
Current form values.

```typescript
// Access form values
const firstName = form.values.firstName;
const email = form.values.email;
```

#### `errors: Partial<Record<keyof TForm, string>>`
Current validation errors.

```typescript
// Check for errors
const hasFirstNameError = Boolean(form.errors.firstName);
const firstNameError = form.errors.firstName;
```

#### `touched: Partial<Record<keyof TForm, boolean>>`
Fields that have been touched (focused and blurred).

```typescript
// Check if field has been touched
const isFirstNameTouched = form.touched.firstName;
```

#### `isDirty: boolean`
Whether the form has been modified from initial values.

```typescript
// Check if form is dirty
if (form.isDirty) {
  // Show unsaved changes warning
}
```

#### `isValid: boolean`
Whether the form is currently valid.

```typescript
// Disable submit button when invalid
<Button disabled={!form.isValid}>Submit</Button>
```

#### `isSubmitting: boolean`
Whether the form is currently being submitted.

```typescript
// Show loading state
<Button disabled={form.isSubmitting}>
  {form.isSubmitting ? 'Submitting...' : 'Submit'}
</Button>
```

#### `setValue: (field: keyof TForm, value: any) => void`
Programmatically set a field value.

```typescript
// Set field value
form.setValue('firstName', 'John');
form.setValue('isActive', true);
```

#### `handleChange: (field: keyof TForm) => (event: any) => void`
Create a change handler for a field.

```typescript
// Use with TextField
<TextField
  value={form.values.firstName}
  onChange={form.handleChange('firstName')}
/>
```

#### `handleBlur: (field: keyof TForm) => (event: any) => void`
Create a blur handler for a field.

```typescript
// Use with TextField
<TextField
  value={form.values.firstName}
  onBlur={form.handleBlur('firstName')}
/>
```

#### `handleSubmit: () => (event: React.FormEvent) => void`
Create a submit handler for the form.

```typescript
// Use with form element
<form onSubmit={form.handleSubmit()}>
  {/* form fields */}
</form>
```

#### `resetForm: () => void`
Reset form to initial values.

```typescript
// Reset form
<Button onClick={form.resetForm}>Reset</Button>
```

## Validation Rules

### IValidationRule Interface

```typescript
interface IValidationRule {
  type: 'required' | 'email' | 'emailStrict' | 'phone' | 'phoneUS' | 'creditCard' | 'cvv' | 'expiryDate' | 'zipUS' | 'zipCanada' | 'passwordStrong' | 'passwordMedium' | 'name' | 'username' | 'url' | 'date' | 'currency' | 'percentage' | 'minLength' | 'maxLength' | 'min' | 'max' | 'pattern' | 'custom';
  value?: any;
  message: string;
  validator?: (value: any, formValues?: any) => boolean;
}
```

### Validation Rule Types

#### Basic Rules

##### `required`
Validates that a field is not empty.

```typescript
{ type: 'required', message: 'This field is required' }
```

##### `minLength`
Validates minimum string length.

```typescript
{ type: 'minLength', value: 5, message: 'Must be at least 5 characters' }
```

##### `maxLength`
Validates maximum string length.

```typescript
{ type: 'maxLength', value: 100, message: 'Must be no more than 100 characters' }
```

##### `min`
Validates minimum numeric value.

```typescript
{ type: 'min', value: 0, message: 'Must be at least 0' }
```

##### `max`
Validates maximum numeric value.

```typescript
{ type: 'max', value: 100, message: 'Must be no more than 100' }
```

#### Email Rules

##### `email`
Standard email validation.

```typescript
{ type: 'email', message: 'Please enter a valid email address' }
```

##### `emailStrict`
Strict email validation (more restrictive).

```typescript
{ type: 'emailStrict', message: 'Please enter a valid email address' }
```

#### Phone Rules

##### `phone`
International phone number validation.

```typescript
{ type: 'phone', message: 'Please enter a valid phone number' }
```

##### `phoneUS`
US phone number validation.

```typescript
{ type: 'phoneUS', message: 'Please enter a valid US phone number' }
```

#### Credit Card Rules

##### `creditCard`
Credit card number validation.

```typescript
{ type: 'creditCard', message: 'Please enter a valid credit card number' }
```

##### `cvv`
CVV validation (3-4 digits).

```typescript
{ type: 'cvv', message: 'CVV must be 3-4 digits' }
```

##### `expiryDate`
Credit card expiry date validation (MM/YY format).

```typescript
{ type: 'expiryDate', message: 'Please enter expiry date in MM/YY format' }
```

#### Address Rules

##### `zipUS`
US ZIP code validation.

```typescript
{ type: 'zipUS', message: 'Please enter a valid US zip code' }
```

##### `zipCanada`
Canadian postal code validation.

```typescript
{ type: 'zipCanada', message: 'Please enter a valid Canadian postal code' }
```

#### Password Rules

##### `passwordStrong`
Strong password validation (uppercase, lowercase, number, special character).

```typescript
{ type: 'passwordStrong', message: 'Password must contain uppercase, lowercase, number and special character' }
```

##### `passwordMedium`
Medium password validation (uppercase, lowercase, number).

```typescript
{ type: 'passwordMedium', message: 'Password must contain uppercase, lowercase and number' }
```

#### Other Rules

##### `name`
Name validation (letters, spaces, hyphens, apostrophes).

```typescript
{ type: 'name', message: 'Please enter a valid name' }
```

##### `username`
Username validation (alphanumeric, hyphens, underscores).

```typescript
{ type: 'username', message: 'Username can only contain letters, numbers, hyphens and underscores' }
```

##### `url`
URL validation.

```typescript
{ type: 'url', message: 'Please enter a valid URL' }
```

##### `date`
Date validation (multiple formats).

```typescript
{ type: 'date', message: 'Please enter a valid date' }
```

##### `currency`
Currency validation.

```typescript
{ type: 'currency', message: 'Please enter a valid currency amount' }
```

##### `percentage`
Percentage validation (0-100).

```typescript
{ type: 'percentage', message: 'Please enter a valid percentage (0-100)' }
```

#### Advanced Rules

##### `pattern`
Custom regex pattern validation.

```typescript
{ 
  type: 'pattern', 
  value: /^[A-Za-z]+$/, 
  message: 'Only letters are allowed' 
}
```

##### `custom`
Custom validation function.

```typescript
{ 
  type: 'custom', 
  message: 'Passwords must match',
  validator: (value, formValues) => value === formValues.password
}
```

## Validation Helpers

### createValidationRules

Helper functions for creating validation rules.

```typescript
import { createValidationRules } from '@/utils/validationHelpers';

// Basic rules
const required = createValidationRules.required();
const email = createValidationRules.email();
const minLength = createValidationRules.minLength(5);

// With custom messages
const customRequired = createValidationRules.required('Custom required message');
const customEmail = createValidationRules.email('Custom email message');
```

### commonValidationSets

Pre-built validation rule sets for common use cases.

```typescript
import { commonValidationSets } from '@/utils/validationHelpers';

// User profile validation
const userProfileRules = commonValidationSets.userProfile;
// Returns: { firstName: [...], lastName: [...], email: [...], phone: [...] }

// Address validation
const addressRules = commonValidationSets.address;
// Returns: { address1: [...], city: [...], state: [...], zip: [...], country: [...] }

// Payment validation
const paymentRules = commonValidationSets.payment;
// Returns: { cardName: [...], cardNumber: [...], expDate: [...], cvv: [...] }

// Authentication validation
const loginRules = commonValidationSets.login;
const registerRules = commonValidationSets.register;
const passwordChangeRules = commonValidationSets.passwordChange;
```

### createFormValidation

Utility function for creating form validation.

```typescript
import { createFormValidation } from '@/utils/validationHelpers';

const validationRules = createFormValidation<MyFormData>({
  firstName: [createValidationRules.required(), createValidationRules.name()],
  email: [createValidationRules.required(), createValidationRules.email()],
});
```

### validateField

Utility function for validating a single field.

```typescript
import { validateField } from '@/utils/validationHelpers';

const rules = [createValidationRules.required(), createValidationRules.email()];
const error = validateField('test@example.com', rules);
// Returns: null if valid, error message if invalid
```

## Validation Patterns

### VALIDATION_PATTERNS

Pre-defined regex patterns for validation.

```typescript
import { VALIDATION_PATTERNS } from '@/types/validation';

// Email patterns
VALIDATION_PATTERNS.EMAIL
VALIDATION_PATTERNS.EMAIL_STRICT

// Phone patterns
VALIDATION_PATTERNS.PHONE_US
VALIDATION_PATTERNS.PHONE_INTERNATIONAL

// Credit card patterns
VALIDATION_PATTERNS.CREDIT_CARD
VALIDATION_PATTERNS.CVV
VALIDATION_PATTERNS.EXPIRY_DATE

// Address patterns
VALIDATION_PATTERNS.ZIP_US
VALIDATION_PATTERNS.ZIP_CANADA
VALIDATION_PATTERNS.POSTAL_CODE

// Password patterns
VALIDATION_PATTERNS.PASSWORD_STRONG
VALIDATION_PATTERNS.PASSWORD_MEDIUM

// Name patterns
VALIDATION_PATTERNS.NAME
VALIDATION_PATTERNS.USERNAME

// Other patterns
VALIDATION_PATTERNS.URL
VALIDATION_PATTERNS.DATE_MM_DD_YYYY
VALIDATION_PATTERNS.DATE_YYYY_MM_DD
VALIDATION_PATTERNS.CURRENCY
VALIDATION_PATTERNS.PERCENTAGE
```

### VALIDATION_MESSAGES

Pre-defined validation messages.

```typescript
import { VALIDATION_MESSAGES } from '@/types/validation';

VALIDATION_MESSAGES.REQUIRED
VALIDATION_MESSAGES.EMAIL
VALIDATION_MESSAGES.EMAIL_STRICT
VALIDATION_MESSAGES.PHONE
VALIDATION_MESSAGES.PHONE_US
VALIDATION_MESSAGES.CREDIT_CARD
VALIDATION_MESSAGES.CVV
VALIDATION_MESSAGES.EXPIRY_DATE
VALIDATION_MESSAGES.ZIP_US
VALIDATION_MESSAGES.ZIP_CANADA
VALIDATION_MESSAGES.PASSWORD_STRONG
VALIDATION_MESSAGES.PASSWORD_MEDIUM
VALIDATION_MESSAGES.NAME
VALIDATION_MESSAGES.USERNAME
VALIDATION_MESSAGES.URL
VALIDATION_MESSAGES.DATE
VALIDATION_MESSAGES.CURRENCY
VALIDATION_MESSAGES.PERCENTAGE
```

## Type Definitions

### Form Data Interface

```typescript
// Define form data interface
interface MyFormData {
  firstName: string;
  lastName: string;
  email: string;
  age: number;
  isActive: boolean;
  tags: string[];
}
```

### Validation Rules Type

```typescript
// Define validation rules type
type MyFormValidationRules = Partial<Record<keyof MyFormData, IValidationRule[]>>;

const validationRules: MyFormValidationRules = {
  firstName: [createValidationRules.required(), createValidationRules.name()],
  lastName: [createValidationRules.required(), createValidationRules.name()],
  email: [createValidationRules.required(), createValidationRules.email()],
  age: [createValidationRules.required(), createValidationRules.min(18)],
  isActive: [], // No validation needed
  tags: [createValidationRules.required()],
};
```

### Form Hook Type

```typescript
// Define form hook type
type MyFormHook = UseAdvancedFormResult<MyFormData>;

const form: MyFormHook = useAdvancedForm<MyFormData>({
  // ... options
});
```

## Error Handling

### Form Submission Errors

```typescript
const form = useAdvancedForm<MyFormData>({
  // ... other options
  onSubmit: async (values) => {
    try {
      await submitData(values);
      // Success handling
    } catch (error) {
      // Error handling
      console.error('Form submission error:', error);
      // Show error notification
    }
  },
});
```

### Validation Errors

```typescript
// Check for validation errors
const hasErrors = Object.keys(form.errors).length > 0;
const hasFieldError = Boolean(form.errors.fieldName);

// Display validation errors
{form.touched.fieldName && form.errors.fieldName && (
  <FormHelperText error>{form.errors.fieldName}</FormHelperText>
)}
```

### Form State Errors

```typescript
// Check form state
const isFormValid = form.isValid;
const isFormDirty = form.isDirty;
const isFormSubmitting = form.isSubmitting;

// Use in UI
<Button disabled={!isFormValid || isFormSubmitting}>
  {isFormSubmitting ? 'Submitting...' : 'Submit'}
</Button>
```

## Performance Considerations

### Validation Optimization

```typescript
// Validate only on blur for better performance
const form = useAdvancedForm<MyFormData>({
  validateOnChange: false,
  validateOnBlur: true,
  // ... other options
});

// Validate on change for real-time feedback
const form = useAdvancedForm<MyFormData>({
  validateOnChange: true,
  validateOnBlur: true,
  // ... other options
});
```

### Memoization

```typescript
// Define validation rules outside component
const validationRules: MyFormValidationRules = {
  firstName: [createValidationRules.required(), createValidationRules.name()],
  email: [createValidationRules.required(), createValidationRules.email()],
};

const MyForm = () => {
  const form = useAdvancedForm<MyFormData>({
    initialValues: { firstName: '', email: '' },
    validationRules, // Reused across renders
    // ... other options
  });
};
```

### Custom Validation Optimization

```typescript
// Use specific validation types when possible
{ type: 'email', message: 'Invalid email' } // ✅ Fast

// Avoid custom validation for common patterns
{ 
  type: 'custom', 
  validator: (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value),
  message: 'Invalid email' 
} // ❌ Slower
```