// Component pattern types

export type KeyedObject = { [key: string]: unknown } & { id?: string | number };

export interface UseTableLogicOptions<T extends KeyedObject> {
  data: T[];
  searchFields?: Array<keyof T & string>;
  defaultOrderBy?: keyof T & string;
  defaultRowsPerPage?: number;
  rowIdentifier?: keyof T & string;
}

export interface UseAdvancedFormOptions<TForm extends object> {
  initialValues: TForm;
  validationRules?: ValidationRules<TForm>;
  onSubmit?: (values: TForm) => Promise<void> | void;
  validateOnChange?: boolean;
  validateOnBlur?: boolean;
}

export type ValidationRuleType =
  | 'required'
  | 'email'
  | 'minLength'
  | 'maxLength'
  | 'pattern'
  | 'custom';

export type ValidationRules<TForm extends object> = Partial<Record<keyof TForm, any[]>>;

// Enterprise component type definitions
import type React from 'react';
import type { ComponentProps } from './enterprise';

// Polymorphic Component Types
export type PolymorphicComponent<T extends React.ElementType> = {
  as?: T;
  children?: React.ReactNode;
} & Omit<React.ComponentPropsWithoutRef<T>, 'as' | 'children'>;

// Compound Component Pattern Types
export type CompoundComponent<T> = React.FC<T> & {
  Header: React.ComponentType<{ children?: React.ReactNode } & Record<string, any>>;
  Body: React.ComponentType<{ children?: React.ReactNode }>;
  Actions?: React.ComponentType<{ children?: React.ReactNode } & Record<string, any>>;
  Footer?: React.ComponentType<{ children?: React.ReactNode }>;
};

// Render Props Pattern Types
export interface RenderProps<T> {
  children: (props: T) => React.ReactNode;
}

// Higher-Order Component Types
export type HOC<TProps = {}> = <TComponent extends React.ComponentType<any>>(
  Component: TComponent
) => React.ComponentType<TProps & ComponentProps<TComponent>>;

// Hook Types with Advanced Patterns
export interface UseAsyncState<T> {
  data: T | null;
  loading: boolean;
  error: Error | null;
  execute: () => Promise<void>;
  reset: () => void;
}

export interface UseFormState<T> {
  values: T;
  errors: Partial<Record<keyof T, string>>;
  touched: Partial<Record<keyof T, boolean>>;
  isValid: boolean;
  isSubmitting: boolean;
  setValue: <K extends keyof T>(field: K, value: T[K]) => void;
  setError: <K extends keyof T>(field: K, error: string) => void;
  handleSubmit: (onSubmit: (values: T) => void) => (e: React.FormEvent) => void;
  resetForm: () => void;
}


