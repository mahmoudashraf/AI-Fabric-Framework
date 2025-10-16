import { useCallback, useMemo, useRef, useState } from 'react';
import type { UseAdvancedFormOptions } from '@/types/components';
import { validateForm, type ValidationErrors } from '@/types/validation';

export interface UseAdvancedFormResult<TForm extends object> {
  values: TForm;
  errors: ValidationErrors<TForm>;
  touched: Partial<Record<keyof TForm, boolean>>;
  isDirty: boolean;
  isValid: boolean;
  isSubmitting: boolean;
  setValue: <K extends keyof TForm>(key: K, value: TForm[K]) => void;
  handleChange: (key: keyof TForm) => (value: unknown) => void;
  handleBlur: (key: keyof TForm) => () => void;
  handleSubmit: (onSubmitOverride?: (values: TForm) => Promise<void> | void) => (e?: { preventDefault?: () => void }) => Promise<void>;
  resetForm: () => void;
}

export function useAdvancedForm<TForm extends Record<string, any>>({
  initialValues,
  validationRules = {},
  onSubmit,
  validateOnChange = true,
  validateOnBlur = true
}: UseAdvancedFormOptions<TForm>): UseAdvancedFormResult<TForm> {
  const [values, setValues] = useState<TForm>(initialValues);
  const [errors, setErrors] = useState<ValidationErrors<TForm>>({});
  const [touched, setTouched] = useState<Partial<Record<keyof TForm, boolean>>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const initialRef = useRef(initialValues);

  const isDirty = useMemo(() => {
    // Safe deep comparison that handles circular references
    const deepEqual = (a: any, b: any): boolean => {
      if (a === b) return true;
      if (a == null || b == null) return false;
      if (typeof a !== typeof b) return false;
      
      // Skip DOM elements and React components
      if (a instanceof HTMLElement || b instanceof HTMLElement) return false;
      if (typeof a === 'object' && a.$$typeof) return false; // React elements
      if (typeof b === 'object' && b.$$typeof) return false; // React elements
      
      if (typeof a === 'object') {
        const keysA = Object.keys(a);
        const keysB = Object.keys(b);
        if (keysA.length !== keysB.length) return false;
        
        for (const key of keysA) {
          if (!keysB.includes(key)) return false;
          if (!deepEqual(a[key], b[key])) return false;
        }
        return true;
      }
      
      return false;
    };
    
    return !deepEqual(values, initialRef.current);
  }, [values]);

  const runValidation = useCallback(
    (nextValues: TForm) => {
      const nextErrors = validateForm(nextValues, validationRules);
      setErrors(nextErrors);
      return nextErrors;
    },
    [validationRules]
  );

  const setValue = useCallback(<K extends keyof TForm>(key: K, value: TForm[K]) => {
    setValues((prev) => {
      const next = { ...prev, [key]: value } as TForm;
      if (validateOnChange) runValidation(next);
      return next;
    });
  }, [runValidation, validateOnChange]);

  const handleChange = useCallback(
    (key: keyof TForm) => (value: unknown) => {
      setValues((prev) => {
        const next = { ...prev, [key]: value } as TForm;
        if (validateOnChange) runValidation(next);
        return next;
      });
    },
    [runValidation, validateOnChange]
  );

  const handleBlur = useCallback(
    (key: keyof TForm) => () => {
      setTouched((prev) => ({ ...prev, [key]: true }));
      if (!validateOnBlur) return;
      setErrors((prev) => ({ ...prev, ...validateForm(values, validationRules) }));
    },
    [values, validationRules, validateOnBlur]
  );

  const isValid = useMemo(() => Object.keys(errors).length === 0, [errors]);

  const resetForm = useCallback(() => {
    setValues(initialRef.current);
    setErrors({});
    setTouched({});
    setIsSubmitting(false);
  }, []);

  const handleSubmit = useCallback(
    (onSubmitOverride?: (values: TForm) => Promise<void> | void) =>
      async (e?: { preventDefault?: () => void }) => {
        if (e && typeof e.preventDefault === 'function') e.preventDefault();
        const nextErrors = runValidation(values);
        if (Object.keys(nextErrors).length > 0) return;
        const submitFn = onSubmitOverride || onSubmit;
        if (!submitFn) return;
        try {
          setIsSubmitting(true);
          await submitFn(values);
        } finally {
          setIsSubmitting(false);
        }
      },
    [values, runValidation, onSubmit]
  );

  return { values, errors, touched, isDirty, isValid, isSubmitting, setValue, handleChange, handleBlur, handleSubmit, resetForm };
}

