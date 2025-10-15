import React from 'react';
import { render, screen } from '@testing-library/react';
import { withLoading } from '../withLoading';

interface TestComponentProps {
  data: string;
  extraProp?: string;
}

const TestComponent: React.FC<TestComponentProps> = ({ data, extraProp }) => (
  <div>
    <span data-testid="data">{data}</span>
    {extraProp && <span data-testid="extra">{extraProp}</span>}
  </div>
);

describe('withLoading HOC', () => {
  describe('Loading state', () => {
    it('renders nothing when loading is true', () => {
      const WrappedComponent = withLoading(TestComponent);
      const { container } = render(<WrappedComponent loading={true} data="test" />);
      
      expect(container.firstChild).toBeNull();
      expect(screen.queryByTestId('data')).not.toBeInTheDocument();
    });

    it('renders component when loading is false', () => {
      const WrappedComponent = withLoading(TestComponent);
      render(<WrappedComponent loading={false} data="test data" />);
      
      expect(screen.getByTestId('data')).toHaveTextContent('test data');
    });

    it('renders component when loading prop is not provided', () => {
      const WrappedComponent = withLoading(TestComponent);
      render(<WrappedComponent data="test data" />);
      
      expect(screen.getByTestId('data')).toHaveTextContent('test data');
    });
  });

  describe('Props forwarding', () => {
    it('forwards all props except loading', () => {
      const WrappedComponent = withLoading(TestComponent);
      render(<WrappedComponent loading={false} data="test data" extraProp="extra value" />);
      
      expect(screen.getByTestId('data')).toHaveTextContent('test data');
      expect(screen.getByTestId('extra')).toHaveTextContent('extra value');
    });

    it('does not pass loading prop to wrapped component', () => {
      const TestComponentWithLoading: React.FC<TestComponentProps & { loading?: boolean }> = (props) => {
        return (
          <div>
            <span data-testid="has-loading">{props.loading !== undefined ? 'has loading' : 'no loading'}</span>
          </div>
        );
      };
      
      const WrappedComponent = withLoading(TestComponentWithLoading);
      render(<WrappedComponent loading={false} data="test" />);
      
      expect(screen.getByTestId('has-loading')).toHaveTextContent('no loading');
    });
  });

  describe('Display name', () => {
    it('sets correct display name', () => {
      const TestComp = () => <div>Test</div>;
      TestComp.displayName = 'TestComp';
      const WrappedComponent = withLoading(TestComp);
      
      expect(WrappedComponent.displayName).toBe('withLoading(TestComp)');
    });

    it('uses component name if display name not set', () => {
      const TestComp = () => <div>Test</div>;
      const WrappedComponent = withLoading(TestComp);
      
      expect(WrappedComponent.displayName).toBe('withLoading(TestComp)');
    });

    it('handles anonymous components', () => {
      const WrappedComponent = withLoading(() => <div>Test</div>);
      
      expect(WrappedComponent.displayName).toBe('withLoading(Component)');
    });
  });

  describe('State transitions', () => {
    it('transitions from loading to loaded state', () => {
      const WrappedComponent = withLoading(TestComponent);
      const { rerender } = render(<WrappedComponent loading={true} data="test data" />);
      
      expect(screen.queryByTestId('data')).not.toBeInTheDocument();
      
      rerender(<WrappedComponent loading={false} data="test data" />);
      
      expect(screen.getByTestId('data')).toHaveTextContent('test data');
    });

    it('transitions from loaded to loading state', () => {
      const WrappedComponent = withLoading(TestComponent);
      const { rerender } = render(<WrappedComponent loading={false} data="test data" />);
      
      expect(screen.getByTestId('data')).toHaveTextContent('test data');
      
      rerender(<WrappedComponent loading={true} data="test data" />);
      
      expect(screen.queryByTestId('data')).not.toBeInTheDocument();
    });
  });

  describe('Component composition', () => {
    it('works with other HOCs', () => {
      const ComponentWithData: React.FC<{ data: string }> = ({ data }) => <div>{data}</div>;
      
      // Simulate wrapping with multiple HOCs
      const WithLoading = withLoading(ComponentWithData);
      
      render(<WithLoading loading={false} data="composed" />);
      expect(screen.getByText('composed')).toBeInTheDocument();
    });
  });

  describe('Edge cases', () => {
    it('handles undefined loading prop', () => {
      const WrappedComponent = withLoading(TestComponent);
      render(<WrappedComponent data="test" />);
      
      expect(screen.getByTestId('data')).toBeInTheDocument();
    });

    it('handles null loading prop', () => {
      const WrappedComponent = withLoading(TestComponent);
      // @ts-expect-error - Testing runtime behavior
      render(<WrappedComponent loading={null} data="test" />);
      
      expect(screen.getByTestId('data')).toBeInTheDocument();
    });

    it('treats 0 as falsy for loading prop', () => {
      const WrappedComponent = withLoading(TestComponent);
      // @ts-expect-error - Testing runtime behavior
      render(<WrappedComponent loading={0} data="test" />);
      
      expect(screen.getByTestId('data')).toBeInTheDocument();
    });

    it('treats empty string as falsy for loading prop', () => {
      const WrappedComponent = withLoading(TestComponent);
      // @ts-expect-error - Testing runtime behavior
      render(<WrappedComponent loading={""} data="test" />);
      
      expect(screen.getByTestId('data')).toBeInTheDocument();
    });
  });
});
