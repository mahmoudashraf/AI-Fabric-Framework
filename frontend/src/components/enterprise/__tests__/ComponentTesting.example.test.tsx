/**
 * Component Testing Examples
 * Demonstrates best practices for testing React components
 */

import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test-utils/enterprise-testing';
import { createTestUser, createTestProduct, createTestCustomer } from '@/test-utils/factories';
import { mockSuccessResponse, mockAsyncOperation } from '@/test-utils/mocks';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { Button, TextField, Card, CardContent, Typography } from '@mui/material';

// ============================================================================
// Example 1: Simple Presentational Component
// ============================================================================

interface User {
  firstName: string;
  lastName: string;
  email: string;
  role: string;
}

const UserCard: React.FC<{ user: User }> = ({ user }) => (
  <Card>
    <CardContent>
      <Typography variant="h6">{user.firstName} {user.lastName}</Typography>
      <Typography variant="body2">{user.email}</Typography>
      <Typography variant="caption">Role: {user.role}</Typography>
    </CardContent>
  </Card>
);

describe('Example 1: Presentational Component', () => {
  it('renders user information correctly', () => {
    const user = createTestUser({
      firstName: 'John',
      lastName: 'Doe',
      email: 'john@example.com',
      role: 'admin'
    });

    render(<UserCard user={user} />);

    expect(screen.getByText('John Doe')).toBeInTheDocument();
    expect(screen.getByText('john@example.com')).toBeInTheDocument();
    expect(screen.getByText('Role: admin')).toBeInTheDocument();
  });

  it('renders with theme provider', () => {
    const theme = createTheme();
    const user = createTestUser();

    render(
      <ThemeProvider theme={theme}>
        <UserCard user={user} />
      </ThemeProvider>
    );

    expect(screen.getByText(/Test User/)).toBeInTheDocument();
  });
});

// ============================================================================
// Example 2: Component with User Interaction
// ============================================================================

const Counter: React.FC = () => {
  const [count, setCount] = React.useState(0);

  return (
    <div>
      <Typography data-testid="count">Count: {count}</Typography>
      <Button onClick={() => setCount(count + 1)}>Increment</Button>
      <Button onClick={() => setCount(count - 1)}>Decrement</Button>
      <Button onClick={() => setCount(0)}>Reset</Button>
    </div>
  );
};

describe('Example 2: Interactive Component', () => {
  it('increments counter on button click', () => {
    render(<Counter />);

    const incrementButton = screen.getByText('Increment');
    
    expect(screen.getByTestId('count')).toHaveTextContent('Count: 0');

    fireEvent.click(incrementButton);
    expect(screen.getByTestId('count')).toHaveTextContent('Count: 1');

    fireEvent.click(incrementButton);
    expect(screen.getByTestId('count')).toHaveTextContent('Count: 2');
  });

  it('decrements counter on button click', () => {
    render(<Counter />);

    const decrementButton = screen.getByText('Decrement');
    
    fireEvent.click(decrementButton);
    expect(screen.getByTestId('count')).toHaveTextContent('Count: -1');
  });

  it('resets counter to zero', () => {
    render(<Counter />);

    fireEvent.click(screen.getByText('Increment'));
    fireEvent.click(screen.getByText('Increment'));
    expect(screen.getByTestId('count')).toHaveTextContent('Count: 2');

    fireEvent.click(screen.getByText('Reset'));
    expect(screen.getByTestId('count')).toHaveTextContent('Count: 0');
  });
});

// ============================================================================
// Example 3: Form Component with Validation
// ============================================================================

const LoginForm: React.FC<{ onSubmit: (data: LoginFormData) => void }> = ({ onSubmit }) => {
  const [email, setEmail] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [error, setError] = React.useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!email || !password) {
      setError('All fields are required');
      return;
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setError('Invalid email format');
      return;
    }

    setError('');
    onSubmit({ email, password });
  };

  return (
    <form onSubmit={handleSubmit}>
      <TextField
        label="Email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        inputProps={{ 'data-testid': 'email-input' }}
        data-testid="email-input-wrapper"
      />
      <TextField
        label="Password"
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        inputProps={{ 'data-testid': 'password-input' }}
        data-testid="password-input-wrapper"
      />
      {error && <Typography color="error" data-testid="error-message">{error}</Typography>}
      <Button type="submit" data-testid="submit-button">Login</Button>
    </form>
  );
};

describe('Example 3: Form Component', () => {
  it('submits form with valid data', () => {
    const handleSubmit = jest.fn();
    render(<LoginForm onSubmit={handleSubmit} />);

    fireEvent.change(screen.getByTestId('email-input'), {
      target: { value: 'test@example.com' }
    });
    fireEvent.change(screen.getByTestId('password-input'), {
      target: { value: 'password123' }
    });
    fireEvent.click(screen.getByTestId('submit-button'));

    expect(handleSubmit).toHaveBeenCalledWith({
      email: 'test@example.com',
      password: 'password123'
    });
  });

  it('shows error for empty fields', () => {
    render(<LoginForm onSubmit={jest.fn()} />);

    fireEvent.click(screen.getByTestId('submit-button'));

    expect(screen.getByTestId('error-message')).toHaveTextContent('All fields are required');
  });

  it('shows error for invalid email', () => {
    render(<LoginForm onSubmit={jest.fn()} />);

    fireEvent.change(screen.getByTestId('email-input'), {
      target: { value: 'invalid-email' }
    });
    fireEvent.change(screen.getByTestId('password-input'), {
      target: { value: 'password123' }
    });
    fireEvent.click(screen.getByTestId('submit-button'));

    expect(screen.getByTestId('error-message')).toHaveTextContent('Invalid email format');
  });

  it('clears error on successful submission', () => {
    render(<LoginForm onSubmit={jest.fn()} />);

    // Trigger error
    fireEvent.click(screen.getByTestId('submit-button'));
    expect(screen.getByTestId('error-message')).toBeInTheDocument();

    // Fix and submit
    fireEvent.change(screen.getByTestId('email-input'), {
      target: { value: 'test@example.com' }
    });
    fireEvent.change(screen.getByTestId('password-input'), {
      target: { value: 'password123' }
    });
    fireEvent.click(screen.getByTestId('submit-button'));

    expect(screen.queryByTestId('error-message')).not.toBeInTheDocument();
  });
});

// ============================================================================
// Example 4: Component with Async Data Loading
// ============================================================================

interface Product {
  id: number;
  name: string;
}

const ProductList: React.FC<{ fetchProducts: () => Promise<Product[]> }> = ({ fetchProducts }) => {
  const [products, setProducts] = React.useState<Product[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState('');

  React.useEffect(() => {
    const loadProducts = async () => {
      try {
        const data = await fetchProducts();
        setProducts(data);
        setLoading(false);
      } catch (err) {
        setError('Failed to load products');
        setLoading(false);
      }
    };

    loadProducts();
  }, [fetchProducts]);

  if (loading) return <Typography>Loading...</Typography>;
  if (error) return <Typography color="error">{error}</Typography>;

  return (
    <div>
      {products.map((product) => (
        <div key={product.id} data-testid={`product-${product.id}`}>
          {product.name}
        </div>
      ))}
    </div>
  );
};

describe('Example 4: Async Component', () => {
  it('shows loading state initially', () => {
    const mockFetch = jest.fn(() => new Promise(() => {})); // Never resolves
    render(<ProductList fetchProducts={mockFetch} />);

    expect(screen.getByText('Loading...')).toBeInTheDocument();
  });

  it('displays products after loading', async () => {
    const products = [
      createTestProduct({ id: 1, name: 'Product 1' }),
      createTestProduct({ id: 2, name: 'Product 2' })
    ];
    const mockFetch = jest.fn().mockResolvedValue(products);

    render(<ProductList fetchProducts={mockFetch} />);

    await waitFor(() => {
      expect(screen.getByTestId('product-1')).toHaveTextContent('Product 1');
      expect(screen.getByTestId('product-2')).toHaveTextContent('Product 2');
    });
  });

  it('shows error message on failure', async () => {
    const mockFetch = jest.fn().mockRejectedValue(new Error('API Error'));

    render(<ProductList fetchProducts={mockFetch} />);

    await waitFor(() => {
      expect(screen.getByText('Failed to load products')).toBeInTheDocument();
    });
  });
});

// ============================================================================
// Example 5: List Component with Search
// ============================================================================

interface SearchUser {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
}

const SearchableUserList: React.FC<{ users: SearchUser[] }> = ({ users }) => {
  const [search, setSearch] = React.useState('');

  const lowered = search.toLowerCase();
  const filteredUsers = users.filter((user) =>
    user.firstName.toLowerCase().includes(lowered) ||
    user.email.toLowerCase().includes(lowered)
  );

  return (
    <div>
      <TextField
        label="Search"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        inputProps={{ 'data-testid': 'search-input' }}
        data-testid="search-input-wrapper"
      />
      <div data-testid="results-count">
        Found: {filteredUsers.length} users
      </div>
      <div>
        {filteredUsers.map((user) => (
          <div key={user.id} data-testid={`user-${user.id}`}>
            {user.firstName} {user.lastName}
          </div>
        ))}
      </div>
    </div>
  );
};

describe('Example 5: Search Component', () => {
  const users = [
    createTestUser({ id: '1', firstName: 'John', lastName: 'Doe', email: 'john@example.com' }),
    createTestUser({ id: '2', firstName: 'Jane', lastName: 'Smith', email: 'jane@example.com' }),
    createTestUser({ id: '3', firstName: 'Bob', lastName: 'Johnson', email: 'bob@example.com' })
  ];

  it('displays all users initially', () => {
    render(<SearchableUserList users={users} />);

    expect(screen.getByTestId('results-count')).toHaveTextContent('Found: 3 users');
    expect(screen.getByTestId('user-1')).toBeInTheDocument();
    expect(screen.getByTestId('user-2')).toBeInTheDocument();
    expect(screen.getByTestId('user-3')).toBeInTheDocument();
  });

  it('filters users by first name', () => {
    render(<SearchableUserList users={users} />);

    fireEvent.change(screen.getByTestId('search-input'), {
      target: { value: 'John' }
    });

    expect(screen.getByTestId('results-count')).toHaveTextContent('Found: 1 users');
    expect(screen.getByTestId('user-1')).toBeInTheDocument();
    expect(screen.queryByTestId('user-2')).not.toBeInTheDocument();
  });

  it('filters users by email', () => {
    render(<SearchableUserList users={users} />);

    fireEvent.change(screen.getByTestId('search-input'), {
      target: { value: 'jane@' }
    });

    expect(screen.getByTestId('results-count')).toHaveTextContent('Found: 1 users');
    expect(screen.getByTestId('user-2')).toBeInTheDocument();
  });

  it('handles case-insensitive search', () => {
    render(<SearchableUserList users={users} />);

    fireEvent.change(screen.getByTestId('search-input'), {
      target: { value: 'BOB' }
    });

    expect(screen.getByTestId('user-3')).toBeInTheDocument();
  });

  it('shows no results when no match', () => {
    render(<SearchableUserList users={users} />);

    fireEvent.change(screen.getByTestId('search-input'), {
      target: { value: 'nonexistent' }
    });

    expect(screen.getByTestId('results-count')).toHaveTextContent('Found: 0 users');
  });
});

// ============================================================================
// Example 6: Component with Context and Async
// ============================================================================

interface Customer {
  name: string;
  email: string;
  orders: number;
  status: number;
}

const CustomerDashboard: React.FC<{ 
  customerId: string;
  fetchCustomer: (id: string) => Promise<Customer>;
}> = ({ customerId, fetchCustomer }) => {
  const [customer, setCustomer] = React.useState<Customer | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        setError(null);
        const data = await fetchCustomer(customerId);
        setCustomer(data);
      } catch (err) {
        setError('Failed to load customer');
      } finally {
        setLoading(false);
      }
    };

    load();
  }, [customerId, fetchCustomer]);

  if (loading) return <div data-testid="loading">Loading...</div>;
  if (error) return <div data-testid="error">{error}</div>;
  if (!customer) return <div data-testid="not-found">Customer not found</div>;

  return (
    <div data-testid="customer-dashboard">
      <Typography variant="h4">{customer.name}</Typography>
      <Typography>Email: {customer.email}</Typography>
      <Typography>Orders: {customer.orders}</Typography>
      <Typography>Status: {customer.status === 1 ? 'Active' : 'Inactive'}</Typography>
    </div>
  );
};

describe('Example 6: Async Component with Context', () => {
  it('shows loading state initially', () => {
    const mockFetch = jest.fn(() => new Promise(() => {}));
    render(<CustomerDashboard customerId="1" fetchCustomer={mockFetch} />);

    expect(screen.getByTestId('loading')).toBeInTheDocument();
  });

  it('displays customer data after loading', async () => {
    const customer = createTestCustomer({
      name: 'John Doe',
      email: 'john@example.com',
      orders: 10,
      status: 1
    });

    const mockFetch = jest.fn().mockResolvedValue(customer);

    render(<CustomerDashboard customerId="1" fetchCustomer={mockFetch} />);

    await waitFor(() => {
      expect(screen.getByTestId('customer-dashboard')).toBeInTheDocument();
    });

    expect(screen.getByText('John Doe')).toBeInTheDocument();
    expect(screen.getByText('Email: john@example.com')).toBeInTheDocument();
    expect(screen.getByText('Orders: 10')).toBeInTheDocument();
    expect(screen.getByText('Status: Active')).toBeInTheDocument();
  });

  it('shows error message on fetch failure', async () => {
    const mockFetch = jest.fn().mockRejectedValue(new Error('Network error'));

    render(<CustomerDashboard customerId="1" fetchCustomer={mockFetch} />);

    await waitFor(() => {
      expect(screen.getByTestId('error')).toHaveTextContent('Failed to load customer');
    });
  });

  it('refetches when customerId changes', async () => {
    const customer1 = createTestCustomer({ id: 1, name: 'Customer 1' });
    const customer2 = createTestCustomer({ id: 2, name: 'Customer 2' });

    const mockFetch = jest.fn()
      .mockResolvedValueOnce(customer1)
      .mockResolvedValueOnce(customer2);

    const { rerender } = render(
      <CustomerDashboard customerId="1" fetchCustomer={mockFetch} />
    );

    await waitFor(() => {
      expect(screen.getByText('Customer 1')).toBeInTheDocument();
    });

    rerender(<CustomerDashboard customerId="2" fetchCustomer={mockFetch} />);

    await waitFor(() => {
      expect(screen.getByText('Customer 2')).toBeInTheDocument();
    });

    expect(mockFetch).toHaveBeenCalledTimes(2);
  });
});

// ============================================================================
// Example 7: Testing with Enterprise Hooks
// ============================================================================

// This example demonstrates how components using enterprise hooks would be tested
// In real scenarios, you'd mock the hook or test the integrated behavior

describe('Example 7: Component with Enterprise Patterns', () => {
  it('demonstrates testing approach', () => {
    // When testing components that use enterprise hooks like useAsyncOperation,
    // you have two approaches:
    
    // Approach 1: Mock the hook
    // jest.mock('@/hooks/enterprise', () => ({
    //   useAsyncOperation: jest.fn(() => ({
    //     data: mockData,
    //     loading: false,
    //     error: null,
    //     execute: jest.fn()
    //   }))
    // }));

    // Approach 2: Test the integrated behavior (better)
    // Render the component and verify it works end-to-end
    // This tests that the component correctly uses the hook
    
    expect(true).toBe(true); // Placeholder
  });
});

// ============================================================================
// Example 8: Accessibility Testing
// ============================================================================

const AccessibleButton: React.FC<{ onClick: () => void; label: string }> = ({ onClick, label }) => (
  <Button
    onClick={onClick}
    aria-label={label}
    role="button"
  >
    {label}
  </Button>
);

describe('Example 8: Accessibility Testing', () => {
  it('has accessible button', () => {
    const handleClick = jest.fn();
    render(<AccessibleButton onClick={handleClick} label="Click Me" />);

    const button = screen.getByRole('button', { name: 'Click Me' });
    expect(button).toBeInTheDocument();
    expect(button).toBeEnabled();
  });

  it('button is clickable', () => {
    const handleClick = jest.fn();
    render(<AccessibleButton onClick={handleClick} label="Click Me" />);

    fireEvent.click(screen.getByRole('button'));
    expect(handleClick).toHaveBeenCalled();
  });
});

// ============================================================================
// Summary: Best Practices Demonstrated
// ============================================================================

/**
 * BEST PRACTICES FROM EXAMPLES:
 * 
 * 1. Use test factories for consistent data
 * 2. Test user behavior, not implementation details
 * 3. Test error scenarios and edge cases
 * 4. Use waitFor for async operations
 * 5. Test accessibility (roles, labels)
 * 6. Use descriptive test names
 * 7. Group related tests with describe blocks
 * 8. Clean up after tests
 * 9. Test component props and interactions
 * 10. Verify rendered output matches expectations
 */
