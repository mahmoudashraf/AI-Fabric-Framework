import { renderHook, act } from '@testing-library/react';
import { useAdvancedForm } from '../useAdvancedForm';
import type { ValidationRules } from '@/types/components';

describe('useAdvancedForm', () => {
  describe('Initialization', () => {
    it('initializes with provided values', () => {
      const initialValues = { email: 'test@example.com', password: '' };
      const { result } = renderHook(() => useAdvancedForm({ initialValues }));

      expect(result.current.values).toEqual(initialValues);
      expect(result.current.errors).toEqual({});
      expect(result.current.touched).toEqual({});
      expect(result.current.isDirty).toBe(false);
      expect(result.current.isValid).toBe(true);
      expect(result.current.isSubmitting).toBe(false);
    });

    it('initializes with empty object', () => {
      const { result } = renderHook(() => useAdvancedForm({ initialValues: {} }));

      expect(result.current.values).toEqual({});
      expect(result.current.isDirty).toBe(false);
    });

    it('provides all required methods', () => {
      const { result } = renderHook(() => useAdvancedForm({ initialValues: {} }));

      expect(typeof result.current.setValue).toBe('function');
      expect(typeof result.current.handleChange).toBe('function');
      expect(typeof result.current.handleBlur).toBe('function');
      expect(typeof result.current.handleSubmit).toBe('function');
      expect(typeof result.current.resetForm).toBe('function');
    });
  });

  describe('Value Management', () => {
    it('updates single field value with setValue', () => {
      const { result } = renderHook(() =>
        useAdvancedForm({ initialValues: { name: '', email: '' } })
      );

      act(() => {
        result.current.setValue('name', 'John Doe');
      });

      expect(result.current.values.name).toBe('John Doe');
      expect(result.current.values.email).toBe('');
    });

    it('updates multiple fields independently', () => {
      const { result } = renderHook(() =>
        useAdvancedForm({ initialValues: { name: '', email: '', age: 0 } })
      );

      act(() => {
        result.current.setValue('name', 'John');
        result.current.setValue('email', 'john@example.com');
        result.current.setValue('age', 25);
      });

      expect(result.current.values).toEqual({
        name: 'John',
        email: 'john@example.com',
        age: 25
      });
    });

    it('updates value with handleChange', () => {
      const { result } = renderHook(() =>
        useAdvancedForm({ initialValues: { email: '' } })
      );

      act(() => {
        const handler = result.current.handleChange('email');
        handler('test@example.com');
      });

      expect(result.current.values.email).toBe('test@example.com');
    });

    it('marks form as dirty after value change', () => {
      const { result } = renderHook(() =>
        useAdvancedForm({ initialValues: { name: '' } })
      );

      expect(result.current.isDirty).toBe(false);

      act(() => {
        result.current.setValue('name', 'John');
      });

      expect(result.current.isDirty).toBe(true);
    });
  });

  describe('Validation', () => {
    const validationRules: ValidationRules<{ email: string; password: string }> = {
      email: [
        { type: 'required', message: 'Email is required' },
        { type: 'email', message: 'Invalid email format' }
      ],
      password: [
        { type: 'required', message: 'Password is required' },
        { type: 'minLength', value: 8, message: 'Password must be at least 8 characters' }
      ]
    };

    it('validates on change when validateOnChange is true', () => {
      const { result } = renderHook(() =>
        useAdvancedForm({
          initialValues: { email: '', password: '' },
          validationRules,
          validateOnChange: true
        })
      );

      act(() => {
        result.current.setValue('email', 'invalid');
      });

      expect(result.current.errors.email).toBeTruthy();
      expect(result.current.isValid).toBe(false);
    });

    it('does not validate on change when validateOnChange is false', () => {
      const { result } = renderHook(() =>
        useAdvancedForm({
          initialValues: { email: '', password: '' },
          validationRules,
          validateOnChange: false
        })
      );

      act(() => {
        result.current.setValue('email', 'invalid');
      });

      expect(result.current.errors.email).toBeUndefined();
      expect(result.current.isValid).toBe(true);
    });

    it('validates on blur when validateOnBlur is true', () => {
      const { result } = renderHook(() =>
        useAdvancedForm({
          initialValues: { email: '', password: '' },
          validationRules,
          validateOnBlur: true,
          validateOnChange: false
        })
      );

      act(() => {
        result.current.setValue('email', 'invalid');
      });

      // No errors yet
      expect(result.current.errors.email).toBeUndefined();

      act(() => {
        const blurHandler = result.current.handleBlur('email');
        blurHandler();
      });

      // Errors after blur
      expect(result.current.errors.email).toBeTruthy();
      expect(result.current.touched.email).toBe(true);
    });

    it('marks field as touched on blur', () => {
      const { result } = renderHook(() =>
        useAdvancedForm({ initialValues: { email: '' } })
      );

      expect(result.current.touched.email).toBeUndefined();

      act(() => {
        const blurHandler = result.current.handleBlur('email');
        blurHandler();
      });

      expect(result.current.touched.email).toBe(true);
    });

    it('validates all fields correctly', () => {
      const { result } = renderHook(() =>
        useAdvancedForm({
          initialValues: { email: '', password: '' },
          validationRules,
          validateOnChange: true
        })
      );

      // Initially invalid (required fields empty)
      act(() => {
        result.current.setValue('email', '');
      });

      expect(result.current.isValid).toBe(false);

      // Valid email, still missing password
      act(() => {
        result.current.setValue('email', 'test@example.com');
      });

      expect(result.current.isValid).toBe(false);

      // Both fields valid
      act(() => {
        result.current.setValue('password', 'password123');
      });

      expect(result.current.isValid).toBe(true);
    });

    it('clears errors when value becomes valid', () => {
      const { result } = renderHook(() =>
        useAdvancedForm({
          initialValues: { email: '' },
          validationRules,
          validateOnChange: true
        })
      );

      // Invalid email
      act(() => {
        result.current.setValue('email', 'invalid');
      });

      expect(result.current.errors.email).toBeTruthy();

      // Valid email
      act(() => {
        result.current.setValue('email', 'valid@example.com');
      });

      expect(result.current.errors.email).toBeUndefined();
      expect(result.current.isValid).toBe(true);
    });
  });

  describe('Form Submission', () => {
    it('calls onSubmit with form values', async () => {
      const onSubmit = jest.fn();
      const { result } = renderHook(() =>
        useAdvancedForm({
          initialValues: { name: 'John', email: 'john@example.com' },
          onSubmit
        })
      );

      await act(async () => {
        const submitHandler = result.current.handleSubmit();
        await submitHandler();
      });

      expect(onSubmit).toHaveBeenCalledWith({
        name: 'John',
        email: 'john@example.com'
      });
    });

    it('prevents default event behavior', async () => {
      const onSubmit = jest.fn();
      const { result } = renderHook(() =>
        useAdvancedForm({ initialValues: { name: '' }, onSubmit })
      );

      const preventDefault = jest.fn();
      const event = { preventDefault };

      await act(async () => {
        const submitHandler = result.current.handleSubmit();
        await submitHandler(event);
      });

      expect(preventDefault).toHaveBeenCalled();
    });

    it('sets isSubmitting during submission', async () => {
      let resolveSubmit: () => void;
      const submitPromise = new Promise<void>((resolve) => {
        resolveSubmit = resolve;
      });

      const onSubmit = jest.fn().mockReturnValue(submitPromise);
      const { result } = renderHook(() =>
        useAdvancedForm({ initialValues: { name: '' }, onSubmit })
      );

      expect(result.current.isSubmitting).toBe(false);

      act(() => {
        const submitHandler = result.current.handleSubmit();
        submitHandler();
      });

      // Should be submitting
      expect(result.current.isSubmitting).toBe(true);

      await act(async () => {
        resolveSubmit!();
        await submitPromise;
      });

      // Should not be submitting anymore
      expect(result.current.isSubmitting).toBe(false);
    });

    it('does not submit if validation fails', async () => {
      const onSubmit = jest.fn();
      const validationRules: ValidationRules<{ email: string }> = {
        email: [{ type: 'required', message: 'Email is required' }]
      };

      const { result } = renderHook(() =>
        useAdvancedForm({
          initialValues: { email: '' },
          validationRules,
          onSubmit
        })
      );

      await act(async () => {
        const submitHandler = result.current.handleSubmit();
        await submitHandler();
      });

      expect(onSubmit).not.toHaveBeenCalled();
      expect(result.current.errors.email).toBeTruthy();
    });

    it('validates before submission', async () => {
      const onSubmit = jest.fn();
      const validationRules: ValidationRules<{ email: string }> = {
        email: [{ type: 'email', message: 'Invalid email' }]
      };

      const { result } = renderHook(() =>
        useAdvancedForm({
          initialValues: { email: 'invalid' },
          validationRules,
          validateOnChange: false,
          onSubmit
        })
      );

      // No errors yet
      expect(result.current.errors.email).toBeUndefined();

      await act(async () => {
        const submitHandler = result.current.handleSubmit();
        await submitHandler();
      });

      // Validation runs on submit
      expect(result.current.errors.email).toBeTruthy();
      expect(onSubmit).not.toHaveBeenCalled();
    });

    it('supports onSubmit override', async () => {
      const originalOnSubmit = jest.fn();
      const overrideOnSubmit = jest.fn();

      const { result } = renderHook(() =>
        useAdvancedForm({
          initialValues: { name: 'John' },
          onSubmit: originalOnSubmit
        })
      );

      await act(async () => {
        const submitHandler = result.current.handleSubmit(overrideOnSubmit);
        await submitHandler();
      });

      expect(overrideOnSubmit).toHaveBeenCalled();
      expect(originalOnSubmit).not.toHaveBeenCalled();
    });

    it('handles submission errors gracefully', async () => {
      const error = new Error('Submission failed');
      const onSubmit = jest.fn().mockRejectedValue(error);

      const { result } = renderHook(() =>
        useAdvancedForm({ initialValues: { name: 'John' }, onSubmit })
      );

      await act(async () => {
        const submitHandler = result.current.handleSubmit();
        try {
          await submitHandler();
        } catch (e) {
          // Expected to throw
        }
      });

      expect(result.current.isSubmitting).toBe(false);
    });
  });

  describe('Form Reset', () => {
    it('resets form to initial values', () => {
      const initialValues = { name: '', email: '' };
      const { result } = renderHook(() =>
        useAdvancedForm({ initialValues })
      );

      act(() => {
        result.current.setValue('name', 'John');
        result.current.setValue('email', 'john@example.com');
      });

      expect(result.current.values).toEqual({
        name: 'John',
        email: 'john@example.com'
      });

      act(() => {
        result.current.resetForm();
      });

      expect(result.current.values).toEqual(initialValues);
      expect(result.current.isDirty).toBe(false);
    });

    it('clears errors on reset', () => {
      const validationRules: ValidationRules<{ email: string }> = {
        email: [{ type: 'email', message: 'Invalid email' }]
      };

      const { result } = renderHook(() =>
        useAdvancedForm({
          initialValues: { email: '' },
          validationRules,
          validateOnChange: true
        })
      );

      act(() => {
        result.current.setValue('email', 'invalid');
      });

      expect(result.current.errors.email).toBeTruthy();

      act(() => {
        result.current.resetForm();
      });

      expect(result.current.errors).toEqual({});
      expect(result.current.isValid).toBe(true);
    });

    it('clears touched state on reset', () => {
      const { result } = renderHook(() =>
        useAdvancedForm({ initialValues: { name: '', email: '' } })
      );

      act(() => {
        const blurHandler = result.current.handleBlur('name');
        blurHandler();
      });

      expect(result.current.touched.name).toBe(true);

      act(() => {
        result.current.resetForm();
      });

      expect(result.current.touched).toEqual({});
    });

    it('resets isSubmitting flag', () => {
      const { result } = renderHook(() =>
        useAdvancedForm({ initialValues: { name: '' } })
      );

      act(() => {
        result.current.resetForm();
      });

      expect(result.current.isSubmitting).toBe(false);
    });
  });

  describe('Edge Cases', () => {
    it('handles complex nested values', () => {
      const initialValues = {
        user: { name: '', email: '' },
        settings: { notifications: true }
      };

      const { result } = renderHook(() =>
        useAdvancedForm({ initialValues })
      );

      act(() => {
        result.current.setValue('user', { name: 'John', email: 'john@example.com' });
      });

      expect(result.current.values.user).toEqual({
        name: 'John',
        email: 'john@example.com'
      });
    });

    it('handles array values', () => {
      const initialValues = { tags: [] as string[] };
      const { result } = renderHook(() =>
        useAdvancedForm({ initialValues })
      );

      act(() => {
        result.current.setValue('tags', ['react', 'typescript']);
      });

      expect(result.current.values.tags).toEqual(['react', 'typescript']);
    });

    it('handles numeric values', () => {
      const { result } = renderHook(() =>
        useAdvancedForm({ initialValues: { age: 0, price: 0 } })
      );

      act(() => {
        result.current.setValue('age', 25);
        result.current.setValue('price', 99.99);
      });

      expect(result.current.values.age).toBe(25);
      expect(result.current.values.price).toBe(99.99);
    });

    it('handles boolean values', () => {
      const { result } = renderHook(() =>
        useAdvancedForm({ initialValues: { accepted: false } })
      );

      act(() => {
        result.current.setValue('accepted', true);
      });

      expect(result.current.values.accepted).toBe(true);
    });

    it('handles null and undefined values', () => {
      const { result } = renderHook(() =>
        useAdvancedForm({ initialValues: { optional: null as string | null } })
      );

      act(() => {
        result.current.setValue('optional', 'value');
      });

      expect(result.current.values.optional).toBe('value');

      act(() => {
        result.current.setValue('optional', null);
      });

      expect(result.current.values.optional).toBe(null);
    });
  });

  describe('Multiple Instances', () => {
    it('manages independent form instances', () => {
      const { result: form1 } = renderHook(() =>
        useAdvancedForm({ initialValues: { name: '' } })
      );

      const { result: form2 } = renderHook(() =>
        useAdvancedForm({ initialValues: { name: '' } })
      );

      act(() => {
        form1.current.setValue('name', 'Form 1');
        form2.current.setValue('name', 'Form 2');
      });

      expect(form1.current.values.name).toBe('Form 1');
      expect(form2.current.values.name).toBe('Form 2');
    });
  });
});
