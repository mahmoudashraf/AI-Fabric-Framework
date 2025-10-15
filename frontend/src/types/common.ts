// Base types
export interface IBaseEntity {
  id: string;
  createdAt: Date;
  updatedAt: Date;
}

// User related types
export interface IUser extends IBaseEntity {
  email: string;
  firstName: string;
  lastName: string;
  avatar?: string;
  role: UserRole;
  isActive: boolean;
  lastLoginAt?: Date;
}

export enum UserRole {
  ADMIN = 'admin',
  USER = 'user',
  MODERATOR = 'moderator',
}

// Authentication types
export interface IAuthState {
  user: IUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
}

export interface ILoginCredentials {
  email: string;
  password: string;
  rememberMe?: boolean;
}

export interface IRegisterData {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  confirmPassword: string;
}

// API Response types
export interface IApiResponse<T = any> {
  data: T;
  message: string;
  success: boolean;
  statusCode: number;
}

export interface IApiError {
  message: string;
  statusCode: number;
  errors?: Record<string, string[]>;
}

// Pagination types
export interface IPaginationParams {
  page: number;
  limit: number;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

export interface IPaginatedResponse<T> {
  data: T[];
  pagination: {
    page: number;
    limit: number;
    total: number;
    totalPages: number;
    hasNext: boolean;
    hasPrev: boolean;
  };
}

// Dashboard types
export interface IDashboardStats {
  totalUsers: number;
  totalRevenue: number;
  totalOrders: number;
  conversionRate: number;
}

export interface IRevenueData {
  period: string;
  amount: number;
  change: number;
  changeType: 'increase' | 'decrease';
}

// Component prop types
export interface IBaseComponentProps {
  className?: string;
  children?: React.ReactNode;
}

export interface IButtonProps extends IBaseComponentProps {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost';
  size?: 'small' | 'medium' | 'large';
  disabled?: boolean;
  loading?: boolean;
  onClick?: () => void;
  type?: 'button' | 'submit' | 'reset';
}

export interface IInputProps extends IBaseComponentProps {
  label?: string;
  placeholder?: string;
  value: string;
  onChange: (value: string) => void;
  onBlur?: () => void;
  error?: string;
  disabled?: boolean;
  required?: boolean;
  type?: 'text' | 'email' | 'password' | 'number' | 'tel' | 'url';
}

export interface IModalProps extends IBaseComponentProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  size?: 'small' | 'medium' | 'large' | 'full';
  closable?: boolean;
}

// Form types
export interface IFormField {
  name: string;
  label: string;
  type: 'text' | 'email' | 'password' | 'number' | 'select' | 'textarea' | 'checkbox' | 'radio';
  value: any;
  error?: string;
  required?: boolean;
  disabled?: boolean;
  placeholder?: string;
  options?: ISelectOption[];
  validation?: IValidationRule[];
}

export interface ISelectOption {
  value: string | number;
  label: string;
  disabled?: boolean;
}

export interface IValidationRule {
  type: 'required' | 'email' | 'emailStrict' | 'phone' | 'phoneUS' | 'creditCard' | 'cvv' | 'expiryDate' | 'zipUS' | 'zipCanada' | 'passwordStrong' | 'passwordMedium' | 'name' | 'username' | 'url' | 'date' | 'currency' | 'percentage' | 'minLength' | 'maxLength' | 'min' | 'max' | 'pattern' | 'custom';
  value?: any;
  message: string;
  validator?: (value: any, formValues?: any) => boolean;
}

// Theme types
export interface IThemeConfig {
  mode: 'light' | 'dark';
  primaryColor: string;
  secondaryColor: string;
  borderRadius: number;
  fontFamily: string;
}

// Navigation types
export interface INavigationItem {
  id: string;
  title: string;
  icon?: React.ComponentType;
  path?: string;
  children?: INavigationItem[];
  permission?: string;
  badge?: {
    text: string;
    color: 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success';
  };
}

// Table types
export interface ITableColumn<T = any> {
  key: string;
  title: string;
  dataIndex: string;
  render?: (value: any, record: T, index: number) => React.ReactNode;
  sortable?: boolean;
  filterable?: boolean;
  width?: number | string;
  align?: 'left' | 'center' | 'right';
}

export interface ITableProps<T = any> {
  columns: ITableColumn<T>[];
  data: T[];
  loading?: boolean;
  pagination?: IPaginationParams;
  onPaginationChange?: (pagination: IPaginationParams) => void;
  onSort?: (sortBy: string, sortOrder: 'asc' | 'desc') => void;
  onFilter?: (filters: Record<string, any>) => void;
  rowKey?: string | ((record: T) => string);
  selectable?: boolean;
  onSelectionChange?: (selectedRows: T[]) => void;
}

// Chart types
export interface IChartData {
  labels: string[];
  datasets: IChartDataset[];
}

export interface IChartDataset {
  label: string;
  data: number[];
  backgroundColor?: string | string[];
  borderColor?: string | string[];
  borderWidth?: number;
  fill?: boolean;
}

export interface IChartOptions {
  responsive: boolean;
  maintainAspectRatio?: boolean;
  plugins?: {
    legend?: {
      position?: 'top' | 'bottom' | 'left' | 'right';
    };
    title?: {
      display: boolean;
      text: string;
    };
  };
  scales?: {
    x?: IChartScale;
    y?: IChartScale;
  };
}

export interface IChartScale {
  beginAtZero?: boolean;
  min?: number;
  max?: number;
  ticks?: {
    stepSize?: number;
  };
}

// Notification types
export interface INotification {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  title: string;
  message: string;
  duration?: number;
  action?: {
    label: string;
    onClick: () => void;
  };
}

// File upload types
export interface IFileUpload {
  file: File;
  progress: number;
  status: 'uploading' | 'success' | 'error';
  url?: string;
  error?: string;
}

export interface IFileUploadProps {
  accept?: string;
  multiple?: boolean;
  maxSize?: number;
  onUpload: (files: IFileUpload[]) => void;
  onError?: (error: string) => void;
}

// Search and filter types
export interface ISearchParams {
  query: string;
  filters: Record<string, any>;
  sortBy: string;
  sortOrder: 'asc' | 'desc';
}

export interface IFilterOption {
  key: string;
  label: string;
  type: 'text' | 'select' | 'date' | 'number' | 'boolean';
  options?: ISelectOption[];
  placeholder?: string;
}

// Error handling types
export interface IErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
  errorInfo: React.ErrorInfo | null;
}

export interface IErrorFallbackProps {
  error: Error;
  resetError: () => void;
}

// Utility types
export type Optional<T, K extends keyof T> = Omit<T, K> & Partial<Pick<T, K>>;
export type RequiredFields<T, K extends keyof T> = T & Required<Pick<T, K>>;
export type DeepPartial<T> = {
  [P in keyof T]?: T[P] extends object ? DeepPartial<T[P]> : T[P];
};

// Event handler types
export type ChangeEventFunc = (event: React.ChangeEvent<HTMLInputElement>) => void;
export type ClickEventFunc = (event: React.MouseEvent<HTMLElement>) => void;
export type SubmitEventFunc = (event: React.FormEvent<HTMLFormElement>) => void;
export type KeyboardEventFunc = (event: React.KeyboardEvent<HTMLElement>) => void;
