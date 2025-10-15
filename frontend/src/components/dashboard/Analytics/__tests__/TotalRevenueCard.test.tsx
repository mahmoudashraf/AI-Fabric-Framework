import { ThemeProvider } from '@mui/material/styles';
import { createTheme } from '@mui/material/styles';
import { render, screen } from '@testing-library/react';

import TotalRevenueCard from '@/components/dashboard/Analytics/TotalRevenueCard';

// Create a test theme
const theme = createTheme();

const renderWithTheme = (component: React.ReactElement) => {
  return render(<ThemeProvider theme={theme}>{component}</ThemeProvider>);
};

describe('TotalRevenueCard', () => {
  it('renders without crashing', () => {
    renderWithTheme(<TotalRevenueCard />);
    expect(screen.getByText('Total Revenue')).toBeInTheDocument();
  });

  it('displays cryptocurrency items', () => {
    renderWithTheme(<TotalRevenueCard />);

    // Check for cryptocurrency names
    expect(screen.getByText('Bitcoin')).toBeInTheDocument();
    expect(screen.getByText('Ethereum')).toBeInTheDocument();
    expect(screen.getByText('Ripple')).toBeInTheDocument();
    expect(screen.getByText('Neo')).toBeInTheDocument();
  });

  it('displays revenue values', () => {
    renderWithTheme(<TotalRevenueCard />);

    // Check for revenue values
    expect(screen.getByText('+ $145.85')).toBeInTheDocument();
    expect(screen.getByText('- $6.368')).toBeInTheDocument();
    expect(screen.getByText('+ $458.63')).toBeInTheDocument();
    expect(screen.getByText('- $5.631')).toBeInTheDocument();
  });

  it('has proper accessibility attributes', () => {
    renderWithTheme(<TotalRevenueCard />);

    const list = screen.getByRole('list');
    expect(list).toHaveAttribute('aria-label', 'main mailbox folders');
  });
});
