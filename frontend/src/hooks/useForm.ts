import { useState, useCallback } from 'react';

export interface IFormField {
  value: string;
  error: string | null;
  touched: boolean;
}

export interface IFormState {
  [key: string]: IFormField;
}

export interface IUseFormReturn {
  formState: IFormState;
  setFieldValue: (fieldName: string, value: string) => void;
  setFieldError: (fieldName: string, error: string | null) => void;
  setFieldTouched: (fieldName: string, touched: boolean) => void;
  handleFieldChange: (
    fieldName: string
  ) => (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => void;
  handleFieldBlur: (fieldName: string) => () => void;
  validateField: (fieldName: string, validator?: (value: string) => string | null) => boolean;
  validateForm: (validators?: Record<string, (value: string) => string | null>) => boolean;
  resetForm: () => void;
  isFormValid: boolean;
  isFieldValid: (fieldName: string) => boolean;
}

/**
 * Custom hook for managing form state with validation
 * @param initialValues - Initial form values
 * @returns Form management utilities
 */
export function useForm(initialValues: Record<string, string> = {}): IUseFormReturn {
  const [formState, setFormState] = useState<IFormState>(() => {
    const state: IFormState = {};
    Object.keys(initialValues).forEach(key => {
      state[key] = {
        value: initialValues[key] || '',
        error: null,
        touched: false,
      };
    });
    return state;
  });

  const setFieldValue = useCallback((fieldName: string, value: string) => {
    setFormState(prev => ({
      ...prev,
      [fieldName]: {
        value,
        error: null, // Clear error when value changes
        touched: prev[fieldName]?.touched || false,
      },
    }));
  }, []);

  const setFieldError = useCallback((fieldName: string, error: string | null) => {
    setFormState(prev => ({
      ...prev,
      [fieldName]: {
        value: prev[fieldName]?.value || '',
        error,
        touched: prev[fieldName]?.touched || false,
      },
    }));
  }, []);

  const setFieldTouched = useCallback((fieldName: string, touched: boolean) => {
    setFormState(prev => ({
      ...prev,
      [fieldName]: {
        value: prev[fieldName]?.value || '',
        error: prev[fieldName]?.error || null,
        touched,
      },
    }));
  }, []);

  const handleFieldChange = useCallback(
    (fieldName: string) => {
      return (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
        setFieldValue(fieldName, event.target.value);
      };
    },
    [setFieldValue]
  );

  const handleFieldBlur = useCallback(
    (fieldName: string) => {
      return () => {
        setFieldTouched(fieldName, true);
      };
    },
    [setFieldTouched]
  );

  const validateField = useCallback(
    (fieldName: string, validator?: (value: string) => string | null): boolean => {
      const field = formState[fieldName];
      if (!field) return false;

      const error = validator ? validator(field.value) : null;
      setFieldError(fieldName, error);
      return !error;
    },
    [formState, setFieldError]
  );

  const validateForm = useCallback(
    (validators?: Record<string, (value: string) => string | null>): boolean => {
      let isValid = true;

      Object.keys(formState).forEach(fieldName => {
        const field = formState[fieldName];
        const validator = validators?.[fieldName];

        if (validator && field) {
          const error = validator(field.value);
          setFieldError(fieldName, error);
          if (error) isValid = false;
        }
      });

      return isValid;
    },
    [formState, setFieldError]
  );

  const resetForm = useCallback(() => {
    setFormState(prev => {
      const resetState: IFormState = {};
      Object.keys(prev).forEach(key => {
        resetState[key] = {
          value: initialValues[key] || '',
          error: null,
          touched: false,
        };
      });
      return resetState;
    });
  }, [initialValues]);

  const isFieldValid = useCallback(
    (fieldName: string): boolean => {
      const field = formState[fieldName];
      return field ? !field.error && field.touched : false;
    },
    [formState]
  );

  const isFormValid = Object.values(formState).every(field => !field.error && field.touched);

  return {
    formState,
    setFieldValue,
    setFieldError,
    setFieldTouched,
    handleFieldChange,
    handleFieldBlur,
    validateField,
    validateForm,
    resetForm,
    isFormValid,
    isFieldValid,
  };
}
