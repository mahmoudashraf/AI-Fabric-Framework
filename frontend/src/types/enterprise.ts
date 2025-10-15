// Enterprise type definitions

// Legacy minimal types removed in favor of unified definitions below

// Advanced TypeScript patterns for enterprise applications
import type React from 'react';

// Generic API Response Types
export interface ApiResponse<T = unknown, E = string> {
  readonly data: T;
  readonly message: string;
  readonly success: boolean;
  readonly statusCode: number;
  readonly error?: E;
  readonly timestamp: string;
  readonly requestId: string;
}

// Utility Types for Enterprise Patterns
export type NonNullableValue<T> = T extends null | undefined ? never : T;
export type DeepPartialDeep<T> = {
  [P in keyof T]?: T[P] extends object ? DeepPartialDeep<T[P]> : T[P];
};
export type DeepRequired<T> = {
  [P in keyof T]-?: T[P] extends object ? DeepRequired<T[P]> : T[P];
};

// Branded Types for Domain Safety
export type UserId = string & { readonly __brand: 'UserId' };
export type ProductId = string & { readonly __brand: 'ProductId' };
export type OrderId = string & { readonly __brand: 'OrderId' };

// Conditional Types for Advanced Patterns
export type ApiEndpoint<T extends string> = T extends `/${infer U}` ? U : never;
export type ExtractApiResponse<T> = T extends ApiResponse<infer U> ? U : never;

// Mapped Types for Component Props
export type ComponentProps<T> = T extends React.ComponentType<infer P> ? P : never;
export type EventHandlers<T> = {
  [K in keyof T as K extends `on${string}` ? K : never]: T[K];
};

// Template Literal Types
export type RoutePattern<T extends string> = T extends `${infer U}/${infer V}`
  ? U | RoutePattern<V>
  : T;

// Advanced Generic Constraints
export interface Repository<TEntity, TId = string> {
  findById(id: TId): Promise<TEntity | null>;
  findAll(): Promise<TEntity[]>;
  create(entity: Omit<TEntity, 'id'>): Promise<TEntity>;
  update(id: TId, entity: Partial<TEntity>): Promise<TEntity>;
  delete(id: TId): Promise<void>;
}

// Discriminated Unions for State Management
export type LoadingState<T> =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: Error };


