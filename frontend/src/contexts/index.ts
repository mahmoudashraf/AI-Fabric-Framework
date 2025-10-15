// Context API exports for migration from Redux
export * from './NotificationContext';
export * from './ThemeContext';
export * from './MenuContext';
export * from './CartContext';

// Phase 1: Complete Context Implementation - NEW PROVIDERS
export * from './KanbanContext';
export * from './CalendarContext';
export * from './ChatContext';
export * from './ContactContext';
export * from './CustomerContext';
export * from './MailContext';
export * from './ProductContext';
export * from './UserContext';

// Keep existing contexts
export * from './ConfigContext';
export * from './JWTContext';
export * from './SupabaseAuthContext';

// Phase 1.2: Property & Marketplace (Batch 2)
// TODO: Implement PropertyContext and MarketplaceContext
// export * from './PropertyContext';
// export * from './MarketplaceContext';
