// Form validation related types and helpers

import type { ValidationRules } from './components';

export type ValidationErrors<TForm extends object> = Partial<Record<keyof TForm, string>>;

// Enhanced validation patterns
export const VALIDATION_PATTERNS = {
  // Email patterns
  EMAIL: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
  EMAIL_STRICT: /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/,
  
  // Phone patterns
  PHONE_US: /^\(?([0-9]{3})\)?[-. ]?([0-9]{3})[-. ]?([0-9]{4})$/,
  PHONE_INTERNATIONAL: /^\+?[1-9]\d{1,14}$/,
  
  // Credit card patterns
  CREDIT_CARD: /^\d{4}\s?\d{4}\s?\d{4}\s?\d{4}$/,
  CVV: /^\d{3,4}$/,
  EXPIRY_DATE: /^(0[1-9]|1[0-2])\/\d{2}$/,
  
  // Address patterns
  ZIP_US: /^\d{5}(-\d{4})?$/,
  ZIP_CANADA: /^[A-Za-z]\d[A-Za-z][ -]?\d[A-Za-z]\d$/,
  POSTAL_CODE: /^[A-Za-z0-9\s-]{3,10}$/,
  
  // Password patterns
  PASSWORD_STRONG: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]/,
  PASSWORD_MEDIUM: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[A-Za-z\d]/,
  
  // Name patterns
  NAME: /^[a-zA-Z\s'-]+$/,
  USERNAME: /^[a-zA-Z0-9_-]+$/,
  
  // URL patterns
  URL: /^https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&//=]*)$/,
  
  // Date patterns
  DATE_MM_DD_YYYY: /^(0[1-9]|1[0-2])\/(0[1-9]|[12][0-9]|3[01])\/\d{4}$/,
  DATE_YYYY_MM_DD: /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$/,
  
  // Currency patterns
  CURRENCY: /^\$?(\d{1,3}(,\d{3})*|(\d+))(\.\d{2})?$/,
  PERCENTAGE: /^(100(\.0{1,2})?|[0-9]{1,2}(\.[0-9]{1,2})?)$/,
} as const;

// Common validation messages
export const VALIDATION_MESSAGES = {
  REQUIRED: 'This field is required',
  EMAIL: 'Please enter a valid email address',
  EMAIL_STRICT: 'Please enter a valid email address',
  PHONE: 'Please enter a valid phone number',
  PHONE_US: 'Please enter a valid US phone number',
  CREDIT_CARD: 'Please enter a valid credit card number',
  CVV: 'CVV must be 3-4 digits',
  EXPIRY_DATE: 'Please enter expiry date in MM/YY format',
  ZIP_US: 'Please enter a valid US zip code',
  ZIP_CANADA: 'Please enter a valid Canadian postal code',
  PASSWORD_STRONG: 'Password must contain uppercase, lowercase, number and special character',
  PASSWORD_MEDIUM: 'Password must contain uppercase, lowercase and number',
  NAME: 'Please enter a valid name',
  USERNAME: 'Username can only contain letters, numbers, hyphens and underscores',
  URL: 'Please enter a valid URL',
  DATE: 'Please enter a valid date',
  CURRENCY: 'Please enter a valid currency amount',
  PERCENTAGE: 'Please enter a valid percentage (0-100)',
} as const;

export function validateForm<TForm extends object>(
  values: TForm,
  rules: ValidationRules<TForm> = {}
): ValidationErrors<TForm> {
  const errors: ValidationErrors<TForm> = {};
  (Object.keys(rules) as Array<keyof TForm>).forEach((key) => {
    // Only validate fields that actually exist on the current values shape.
    // This aligns validation with dynamic forms and tests that only set a subset of fields.
    if (!(key in (values as any))) return;
    const fieldRules = (rules as any)[key] as any[] | undefined;
    if (!fieldRules) return;
    const value = (values as any)[key];
    for (const rule of fieldRules) {
      switch (rule.type) {
        case 'required':
          if (value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'email':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.EMAIL.test(value)
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'emailStrict':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.EMAIL_STRICT.test(value)
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'phone':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.PHONE_INTERNATIONAL.test(value.replace(/[\s\-\(\)]/g, ''))
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'phoneUS':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.PHONE_US.test(value)
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'creditCard':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.CREDIT_CARD.test(value)
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'cvv':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.CVV.test(value)
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'expiryDate':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.EXPIRY_DATE.test(value)
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'zipUS':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.ZIP_US.test(value)
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'zipCanada':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.ZIP_CANADA.test(value)
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'passwordStrong':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.PASSWORD_STRONG.test(value)
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'passwordMedium':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.PASSWORD_MEDIUM.test(value)
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'name':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.NAME.test(value)
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'username':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.USERNAME.test(value)
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'url':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.URL.test(value)
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'date':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.DATE_MM_DD_YYYY.test(value) &&
            !VALIDATION_PATTERNS.DATE_YYYY_MM_DD.test(value)
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'currency':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.CURRENCY.test(value)
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'percentage':
          if (
            typeof value === 'string' &&
            !VALIDATION_PATTERNS.PERCENTAGE.test(value)
          ) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'minLength':
          if (typeof value === 'string' && value.length < rule.value) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'maxLength':
          if (typeof value === 'string' && value.length > rule.value) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'min':
          if (typeof value === 'number' && value < rule.value) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'max':
          if (typeof value === 'number' && value > rule.value) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'pattern':
          if (typeof value === 'string' && !rule.value.test(value)) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
        case 'custom':
          if (!rule.validator(value, values)) {
            (errors as any)[key] = rule.message;
            return;
          }
          break;
      }
    }
  });
  return errors;
}

