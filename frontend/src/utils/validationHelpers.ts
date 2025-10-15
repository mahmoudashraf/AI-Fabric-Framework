// Enhanced validation helpers for enterprise forms

import { VALIDATION_PATTERNS, VALIDATION_MESSAGES } from '@/types/validation';
import type { IValidationRule } from '@/types/common';

// Common validation rule builders
export const createValidationRules = {
  // Required field validation
  required: (message?: string): IValidationRule => ({
    type: 'required',
    message: message || VALIDATION_MESSAGES.REQUIRED,
  }),

  // Email validation
  email: (message?: string): IValidationRule => ({
    type: 'email',
    message: message || VALIDATION_MESSAGES.EMAIL,
  }),

  emailStrict: (message?: string): IValidationRule => ({
    type: 'emailStrict',
    message: message || VALIDATION_MESSAGES.EMAIL_STRICT,
  }),

  // Phone validation
  phone: (message?: string): IValidationRule => ({
    type: 'phone',
    message: message || VALIDATION_MESSAGES.PHONE,
  }),

  phoneUS: (message?: string): IValidationRule => ({
    type: 'phoneUS',
    message: message || VALIDATION_MESSAGES.PHONE_US,
  }),

  // Credit card validation
  creditCard: (message?: string): IValidationRule => ({
    type: 'creditCard',
    message: message || VALIDATION_MESSAGES.CREDIT_CARD,
  }),

  cvv: (message?: string): IValidationRule => ({
    type: 'cvv',
    message: message || VALIDATION_MESSAGES.CVV,
  }),

  expiryDate: (message?: string): IValidationRule => ({
    type: 'expiryDate',
    message: message || VALIDATION_MESSAGES.EXPIRY_DATE,
  }),

  // Address validation
  zipUS: (message?: string): IValidationRule => ({
    type: 'zipUS',
    message: message || VALIDATION_MESSAGES.ZIP_US,
  }),

  zipCanada: (message?: string): IValidationRule => ({
    type: 'zipCanada',
    message: message || VALIDATION_MESSAGES.ZIP_CANADA,
  }),

  // Password validation
  passwordStrong: (message?: string): IValidationRule => ({
    type: 'passwordStrong',
    message: message || VALIDATION_MESSAGES.PASSWORD_STRONG,
  }),

  passwordMedium: (message?: string): IValidationRule => ({
    type: 'passwordMedium',
    message: message || VALIDATION_MESSAGES.PASSWORD_MEDIUM,
  }),

  // Name validation
  name: (message?: string): IValidationRule => ({
    type: 'name',
    message: message || VALIDATION_MESSAGES.NAME,
  }),

  username: (message?: string): IValidationRule => ({
    type: 'username',
    message: message || VALIDATION_MESSAGES.USERNAME,
  }),

  // URL validation
  url: (message?: string): IValidationRule => ({
    type: 'url',
    message: message || VALIDATION_MESSAGES.URL,
  }),

  // Date validation
  date: (message?: string): IValidationRule => ({
    type: 'date',
    message: message || VALIDATION_MESSAGES.DATE,
  }),

  // Currency validation
  currency: (message?: string): IValidationRule => ({
    type: 'currency',
    message: message || VALIDATION_MESSAGES.CURRENCY,
  }),

  percentage: (message?: string): IValidationRule => ({
    type: 'percentage',
    message: message || VALIDATION_MESSAGES.PERCENTAGE,
  }),

  // Length validation
  minLength: (min: number, message?: string): IValidationRule => ({
    type: 'minLength',
    value: min,
    message: message || `Must be at least ${min} characters`,
  }),

  maxLength: (max: number, message?: string): IValidationRule => ({
    type: 'maxLength',
    value: max,
    message: message || `Must be no more than ${max} characters`,
  }),

  // Numeric validation
  min: (min: number, message?: string): IValidationRule => ({
    type: 'min',
    value: min,
    message: message || `Must be at least ${min}`,
  }),

  max: (max: number, message?: string): IValidationRule => ({
    type: 'max',
    value: max,
    message: message || `Must be no more than ${max}`,
  }),

  // Pattern validation
  pattern: (pattern: RegExp, message: string): IValidationRule => ({
    type: 'pattern',
    value: pattern,
    message,
  }),

  // Custom validation
  custom: (validator: (value: any, formValues?: any) => boolean, message: string): IValidationRule => ({
    type: 'custom',
    validator,
    message,
  }),
};

// Pre-built validation rule sets for common use cases
export const commonValidationSets = {
  // User profile validation
  userProfile: {
    firstName: [createValidationRules.required(), createValidationRules.name()],
    lastName: [createValidationRules.required(), createValidationRules.name()],
    email: [createValidationRules.required(), createValidationRules.emailStrict()],
    phone: [createValidationRules.phoneUS()],
  },

  // Address validation
  address: {
    address1: [createValidationRules.required(), createValidationRules.minLength(5)],
    city: [createValidationRules.required(), createValidationRules.name()],
    state: [createValidationRules.name()],
    zip: [createValidationRules.required(), createValidationRules.zipUS()],
    country: [createValidationRules.required(), createValidationRules.name()],
  },

  // Payment validation
  payment: {
    cardName: [createValidationRules.required(), createValidationRules.name()],
    cardNumber: [createValidationRules.required(), createValidationRules.creditCard()],
    expDate: [createValidationRules.required(), createValidationRules.expiryDate()],
    cvv: [createValidationRules.required(), createValidationRules.cvv()],
  },

  // Authentication validation
  login: {
    email: [createValidationRules.required(), createValidationRules.email()],
    password: [createValidationRules.required(), createValidationRules.minLength(8)],
  },

  register: {
    firstName: [createValidationRules.required(), createValidationRules.name()],
    lastName: [createValidationRules.required(), createValidationRules.name()],
    email: [createValidationRules.required(), createValidationRules.emailStrict()],
    password: [createValidationRules.required(), createValidationRules.passwordStrong()],
    confirmPassword: [createValidationRules.required()],
  },

  // Password change validation
  passwordChange: {
    currentPassword: [createValidationRules.required()],
    newPassword: [createValidationRules.required(), createValidationRules.passwordStrong()],
    confirmPassword: [createValidationRules.required()],
  },
};

// Utility function to create validation rules for a form
export function createFormValidation<T extends Record<string, any>>(
  rules: Partial<Record<keyof T, IValidationRule[]>>
): Partial<Record<keyof T, IValidationRule[]>> {
  return rules;
}

// Utility function to validate a single field
export function validateField(
  value: any,
  rules: IValidationRule[],
  formValues?: any
): string | null {
  for (const rule of rules) {
    switch (rule.type) {
      case 'required':
        if (value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)) {
          return rule.message;
        }
        break;
      case 'email':
        if (typeof value === 'string' && !VALIDATION_PATTERNS.EMAIL.test(value)) {
          return rule.message;
        }
        break;
      case 'emailStrict':
        if (typeof value === 'string' && !VALIDATION_PATTERNS.EMAIL_STRICT.test(value)) {
          return rule.message;
        }
        break;
      case 'phone':
        if (typeof value === 'string' && !VALIDATION_PATTERNS.PHONE_INTERNATIONAL.test(value.replace(/[\s\-\(\)]/g, ''))) {
          return rule.message;
        }
        break;
      case 'phoneUS':
        if (typeof value === 'string' && !VALIDATION_PATTERNS.PHONE_US.test(value)) {
          return rule.message;
        }
        break;
      case 'creditCard':
        if (typeof value === 'string' && !VALIDATION_PATTERNS.CREDIT_CARD.test(value)) {
          return rule.message;
        }
        break;
      case 'cvv':
        if (typeof value === 'string' && !VALIDATION_PATTERNS.CVV.test(value)) {
          return rule.message;
        }
        break;
      case 'expiryDate':
        if (typeof value === 'string' && !VALIDATION_PATTERNS.EXPIRY_DATE.test(value)) {
          return rule.message;
        }
        break;
      case 'zipUS':
        if (typeof value === 'string' && !VALIDATION_PATTERNS.ZIP_US.test(value)) {
          return rule.message;
        }
        break;
      case 'zipCanada':
        if (typeof value === 'string' && !VALIDATION_PATTERNS.ZIP_CANADA.test(value)) {
          return rule.message;
        }
        break;
      case 'passwordStrong':
        if (typeof value === 'string' && !VALIDATION_PATTERNS.PASSWORD_STRONG.test(value)) {
          return rule.message;
        }
        break;
      case 'passwordMedium':
        if (typeof value === 'string' && !VALIDATION_PATTERNS.PASSWORD_MEDIUM.test(value)) {
          return rule.message;
        }
        break;
      case 'name':
        if (typeof value === 'string' && !VALIDATION_PATTERNS.NAME.test(value)) {
          return rule.message;
        }
        break;
      case 'username':
        if (typeof value === 'string' && !VALIDATION_PATTERNS.USERNAME.test(value)) {
          return rule.message;
        }
        break;
      case 'url':
        if (typeof value === 'string' && !VALIDATION_PATTERNS.URL.test(value)) {
          return rule.message;
        }
        break;
      case 'date':
        if (typeof value === 'string' && 
            !VALIDATION_PATTERNS.DATE_MM_DD_YYYY.test(value) && 
            !VALIDATION_PATTERNS.DATE_YYYY_MM_DD.test(value)) {
          return rule.message;
        }
        break;
      case 'currency':
        if (typeof value === 'string' && !VALIDATION_PATTERNS.CURRENCY.test(value)) {
          return rule.message;
        }
        break;
      case 'percentage':
        if (typeof value === 'string' && !VALIDATION_PATTERNS.PERCENTAGE.test(value)) {
          return rule.message;
        }
        break;
      case 'minLength':
        if (typeof value === 'string' && value.length < rule.value) {
          return rule.message;
        }
        break;
      case 'maxLength':
        if (typeof value === 'string' && value.length > rule.value) {
          return rule.message;
        }
        break;
      case 'min':
        if (typeof value === 'number' && value < rule.value) {
          return rule.message;
        }
        break;
      case 'max':
        if (typeof value === 'number' && value > rule.value) {
          return rule.message;
        }
        break;
      case 'pattern':
        if (typeof value === 'string' && !rule.value.test(value)) {
          return rule.message;
        }
        break;
      case 'custom':
        if (rule.validator && !rule.validator(value, formValues)) {
          return rule.message;
        }
        break;
    }
  }
  return null;
}