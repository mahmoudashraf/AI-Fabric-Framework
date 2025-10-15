# Form Modernization Quick Reference

## 🚀 Quick Start Template

```typescript
import { useAdvancedForm } from '@/hooks/enterprise';
import { useNotifications } from 'contexts/NotificationContext';
import { createValidationRules } from '@/utils/validationHelpers';
import type { IValidationRule } from '@/types/common';

// 1. Define form interface
interface MyFormData {
  field1: string;
  field2: string;
}

// 2. Define validation rules
const validationRules: Partial<Record<keyof MyFormData, IValidationRule[]>> = {
  field1: [createValidationRules.required(), createValidationRules.email()],
  field2: [createValidationRules.required(), createValidationRules.minLength(5)],
};

// 3. Use in component
const MyForm = () => {
  const notifications = useNotifications();
  
  const form = useAdvancedForm<MyFormData>({
    initialValues: { field1: '', field2: '' },
    validationRules,
    onSubmit: async (values) => {
      try {
        await submitData(values);
        notifications.showNotification({
          message: 'Success!',
          variant: 'success',
          alert: { color: 'success', variant: 'filled' },
          close: true,
        });
      } catch (error) {
        notifications.showNotification({
          message: 'Error occurred',
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
        name="field1"
        value={form.values.field1}
        onChange={form.handleChange('field1')}
        onBlur={form.handleBlur('field1')}
        error={Boolean(form.touched.field1 && form.errors.field1)}
        helperText={form.touched.field1 && form.errors.field1}
        fullWidth
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

## 📋 Validation Rules Cheat Sheet

### Basic Rules
```typescript
// Required field
createValidationRules.required()
createValidationRules.required('Custom message')

// Length validation
createValidationRules.minLength(5)
createValidationRules.maxLength(100)

// Numeric validation
createValidationRules.min(0)
createValidationRules.max(100)
```

### Email & Phone
```typescript
// Email validation
createValidationRules.email()
createValidationRules.emailStrict()

// Phone validation
createValidationRules.phone()
createValidationRules.phoneUS()
```

### Credit Card
```typescript
// Credit card validation
createValidationRules.creditCard()
createValidationRules.cvv()
createValidationRules.expiryDate()
```

### Address
```typescript
// Address validation
createValidationRules.zipUS()
createValidationRules.zipCanada()
```

### Password
```typescript
// Password validation
createValidationRules.passwordStrong()
createValidationRules.passwordMedium()
```

### Other Types
```typescript
// Name validation
createValidationRules.name()
createValidationRules.username()

// URL validation
createValidationRules.url()

// Date validation
createValidationRules.date()

// Currency validation
createValidationRules.currency()
createValidationRules.percentage()
```

### Custom Validation
```typescript
// Custom validation
createValidationRules.custom(
  (value, formValues) => value === formValues.password,
  'Passwords must match'
)

// Pattern validation
createValidationRules.pattern(/^[A-Z]+$/, 'Only uppercase letters allowed')
```

## 🎯 Pre-built Validation Sets

```typescript
import { commonValidationSets } from '@/utils/validationHelpers';

// User profile validation
const userProfileRules = commonValidationSets.userProfile;

// Address validation
const addressRules = commonValidationSets.address;

// Payment validation
const paymentRules = commonValidationSets.payment;

// Authentication validation
const loginRules = commonValidationSets.login;
const registerRules = commonValidationSets.register;
const passwordChangeRules = commonValidationSets.passwordChange;
```

## 🔧 Form State Properties

```typescript
// Form values
form.values.fieldName

// Form errors
form.errors.fieldName

// Form touched state
form.touched.fieldName

// Form state flags
form.isDirty        // Has form been modified?
form.isValid        // Is form valid?
form.isSubmitting   // Is form being submitted?

// Form actions
form.setValue('fieldName', value)
form.handleChange('fieldName')
form.handleBlur('fieldName')
form.handleSubmit()
form.resetForm()
```

## 📝 Common Patterns

### Text Field
```typescript
<TextField
  name="fieldName"
  label="Field Label"
  value={form.values.fieldName}
  onChange={form.handleChange('fieldName')}
  onBlur={form.handleBlur('fieldName')}
  error={Boolean(form.touched.fieldName && form.errors.fieldName)}
  helperText={form.touched.fieldName && form.errors.fieldName}
  fullWidth
  required
/>
```

### Select Field
```typescript
<FormControl fullWidth>
  <InputLabel>Select Option</InputLabel>
  <Select
    value={form.values.fieldName}
    onChange={(event) => form.setValue('fieldName', event.target.value)}
    onBlur={form.handleBlur('fieldName')}
    error={Boolean(form.touched.fieldName && form.errors.fieldName)}
  >
    <MenuItem value="option1">Option 1</MenuItem>
    <MenuItem value="option2">Option 2</MenuItem>
  </Select>
  {form.touched.fieldName && form.errors.fieldName && (
    <FormHelperText error>{form.errors.fieldName}</FormHelperText>
  )}
</FormControl>
```

### Checkbox Field
```typescript
<FormControlLabel
  control={
    <Checkbox
      checked={form.values.fieldName}
      onChange={(event) => form.setValue('fieldName', event.target.checked)}
    />
  }
  label="Checkbox Label"
/>
```

### Submit Button
```typescript
<Button
  type="submit"
  variant="contained"
  disabled={form.isSubmitting || !form.isValid}
  fullWidth
>
  {form.isSubmitting ? 'Submitting...' : 'Submit'}
</Button>
```

## 🚨 Common Issues & Solutions

### TypeScript Errors
```typescript
// ❌ Error: Property 'email' does not exist
const form = useAdvancedForm<{}>({
  initialValues: { email: '' },
});

// ✅ Solution: Define proper interface
interface MyFormData {
  email: string;
}
const form = useAdvancedForm<MyFormData>({
  initialValues: { email: '' },
});
```

### Validation Not Working
```typescript
// ❌ Missing required validation
const validationRules = {
  email: [{ type: 'email', message: 'Invalid email' }],
};

// ✅ Add required validation
const validationRules = {
  email: [
    { type: 'required', message: 'Email is required' },
    { type: 'email', message: 'Invalid email' }
  ],
};
```

### Form Not Submitting
```typescript
// ❌ Missing form element
<Button onClick={handleSubmit}>Submit</Button>

// ✅ Use form element
<form onSubmit={form.handleSubmit()}>
  <Button type="submit">Submit</Button>
</form>
```

### Error Messages Not Showing
```typescript
// ❌ Missing error binding
<TextField
  value={form.values.email}
  onChange={form.handleChange('email')}
/>

// ✅ Add error binding
<TextField
  value={form.values.email}
  onChange={form.handleChange('email')}
  onBlur={form.handleBlur('email')}
  error={Boolean(form.touched.email && form.errors.email)}
  helperText={form.touched.email && form.errors.email}
/>
```

## 📚 File Locations

- **Hook**: `/src/hooks/enterprise/useAdvancedForm.ts`
- **Types**: `/src/types/common.ts`
- **Validation**: `/src/types/validation.ts`
- **Helpers**: `/src/utils/validationHelpers.ts`
- **Examples**: `/src/components/forms/forms-validation/`

## 🎯 Migration Checklist

- [ ] Replace `useFormik` with `useAdvancedForm`
- [ ] Define TypeScript interface for form data
- [ ] Convert Yup schema to validation rules
- [ ] Add `onBlur` handlers to all fields
- [ ] Update error binding (`Boolean(form.touched.field && form.errors.field)`)
- [ ] Add loading states to submit button
- [ ] Add success/error notifications
- [ ] Test form validation and submission
- [ ] Remove Formik and Yup dependencies

## 🔍 Debugging Tips

1. **Check console for validation errors**
2. **Verify form state with `console.log(form)`**
3. **Ensure all required fields have validation rules**
4. **Check that `onSubmit` is properly defined**
5. **Verify form element wraps submit button**
6. **Test with invalid data to see error messages**