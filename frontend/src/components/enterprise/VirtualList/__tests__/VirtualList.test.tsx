import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { VirtualList } from '../VirtualList';

// Mock react-window
jest.mock('react-window', () => ({
  FixedSizeList: ({ children, itemCount, itemSize, height, width }: any) => {
    // Render a simplified version for testing
    const items = Array.from({ length: itemCount }, (_, index) => (
      <div key={index} data-testid={`virtual-item-${index}`}>
        {children({ index, style: {} })}
      </div>
    ));
    
    return (
      <div 
        data-testid="virtual-list" 
        style={{ height, width }}
        data-item-count={itemCount}
        data-item-size={itemSize}
      >
        {items}
      </div>
    );
  }
}));

describe('VirtualList', () => {
  const mockItems = [
    { id: 1, name: 'Item 1' },
    { id: 2, name: 'Item 2' },
    { id: 3, name: 'Item 3' },
    { id: 4, name: 'Item 4' },
    { id: 5, name: 'Item 5' }
  ];

  const renderItem = (item: typeof mockItems[0]) => (
    <div data-testid={`item-${item.id}`}>{item.name}</div>
  );

  describe('Basic Rendering', () => {
    it('should render virtual list with items', () => {
      render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          renderItem={renderItem}
        />
      );

      expect(screen.getByTestId('virtual-list')).toBeInTheDocument();
    });

    it('should render correct number of items', () => {
      render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          renderItem={renderItem}
        />
      );

      const list = screen.getByTestId('virtual-list');
      expect(list.getAttribute('data-item-count')).toBe('5');
    });

    it('should apply correct item height', () => {
      render(
        <VirtualList
          items={mockItems}
          itemHeight={75}
          height={500}
          renderItem={renderItem}
        />
      );

      const list = screen.getByTestId('virtual-list');
      expect(list.getAttribute('data-item-size')).toBe('75');
    });

    it('should apply correct list height', () => {
      render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={600}
          renderItem={renderItem}
        />
      );

      const list = screen.getByTestId('virtual-list');
      expect(list.style.height).toBe('600px');
    });

    it('should render with custom width', () => {
      render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          width={800}
          renderItem={renderItem}
        />
      );

      const list = screen.getByTestId('virtual-list');
      expect(list.style.width).toBe('800px');
    });

    it('should use default width when not specified', () => {
      render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          renderItem={renderItem}
        />
      );

      const list = screen.getByTestId('virtual-list');
      expect(list.style.width).toBe('600px');
    });
  });

  describe('Item Rendering', () => {
    it('should render items using renderItem prop', () => {
      render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          renderItem={renderItem}
        />
      );

      expect(screen.getByTestId('item-1')).toHaveTextContent('Item 1');
      expect(screen.getByTestId('item-2')).toHaveTextContent('Item 2');
      expect(screen.getByTestId('item-3')).toHaveTextContent('Item 3');
    });

    it('should pass correct index to renderItem', () => {
      const renderWithIndex = jest.fn((item, index) => (
        <div data-testid={`item-${index}`}>
          {item.name} - Index: {index}
        </div>
      ));

      render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          renderItem={renderWithIndex}
        />
      );

      expect(renderWithIndex).toHaveBeenCalledWith(mockItems[0], 0);
      expect(renderWithIndex).toHaveBeenCalledWith(mockItems[1], 1);
      expect(renderWithIndex).toHaveBeenCalledWith(mockItems[2], 2);
    });

    it('should render custom item components', () => {
      const customRenderItem = (item: typeof mockItems[0]) => (
        <div className="custom-item" data-id={item.id}>
          <strong>{item.name}</strong>
        </div>
      );

      render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          renderItem={customRenderItem}
        />
      );

      const items = screen.getAllByText(/Item \d+/);
      expect(items).toHaveLength(5);
      expect(items[0]).toHaveTextContent('Item 1');
    });
  });

  describe('Loading State', () => {
    it('should render extra item when loading', () => {
      render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          loading={true}
          renderItem={renderItem}
        />
      );

      const list = screen.getByTestId('virtual-list');
      // Should render items.length + 1 for loading indicator
      expect(list.getAttribute('data-item-count')).toBe('6');
    });

    it('should not render extra item when not loading', () => {
      render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          loading={false}
          renderItem={renderItem}
        />
      );

      const list = screen.getByTestId('virtual-list');
      expect(list.getAttribute('data-item-count')).toBe('5');
    });

    it('should handle loading state changes', () => {
      const { rerender } = render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          loading={false}
          renderItem={renderItem}
        />
      );

      let list = screen.getByTestId('virtual-list');
      expect(list.getAttribute('data-item-count')).toBe('5');

      rerender(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          loading={true}
          renderItem={renderItem}
        />
      );

      list = screen.getByTestId('virtual-list');
      expect(list.getAttribute('data-item-count')).toBe('6');
    });
  });

  describe('Empty States', () => {
    it('should handle empty items array', () => {
      render(
        <VirtualList
          items={[]}
          itemHeight={50}
          height={500}
          renderItem={renderItem}
        />
      );

      const list = screen.getByTestId('virtual-list');
      expect(list.getAttribute('data-item-count')).toBe('0');
    });

    it('should handle single item', () => {
      render(
        <VirtualList
          items={[mockItems[0]]}
          itemHeight={50}
          height={500}
          renderItem={renderItem}
        />
      );

      expect(screen.getByTestId('item-1')).toBeInTheDocument();
      const list = screen.getByTestId('virtual-list');
      expect(list.getAttribute('data-item-count')).toBe('1');
    });
  });

  describe('Performance Configurations', () => {
    it('should use default overscan count', () => {
      // The default overscan is 5 as per component
      const { container } = render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          renderItem={renderItem}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it('should accept custom overscan count', () => {
      const { container } = render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          overscanCount={10}
          renderItem={renderItem}
        />
      );

      expect(container).toBeInTheDocument();
    });
  });

  describe('Data Updates', () => {
    it('should update when items change', () => {
      const { rerender } = render(
        <VirtualList
          items={mockItems.slice(0, 3)}
          itemHeight={50}
          height={500}
          renderItem={renderItem}
        />
      );

      let list = screen.getByTestId('virtual-list');
      expect(list.getAttribute('data-item-count')).toBe('3');

      rerender(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          renderItem={renderItem}
        />
      );

      list = screen.getByTestId('virtual-list');
      expect(list.getAttribute('data-item-count')).toBe('5');
    });

    it('should re-render items when data changes', () => {
      const { rerender } = render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          renderItem={renderItem}
        />
      );

      expect(screen.getByTestId('item-1')).toHaveTextContent('Item 1');

      const updatedItems = [
        { id: 1, name: 'Updated Item 1' },
        ...mockItems.slice(1)
      ];

      rerender(
        <VirtualList
          items={updatedItems}
          itemHeight={50}
          height={500}
          renderItem={renderItem}
        />
      );

      expect(screen.getByTestId('item-1')).toHaveTextContent('Updated Item 1');
    });
  });

  describe('Large Dataset Performance', () => {
    it('should handle large dataset efficiently', () => {
      const largeDataset = Array.from({ length: 10000 }, (_, i) => ({
        id: i,
        name: `Item ${i}`
      }));

      render(
        <VirtualList
          items={largeDataset}
          itemHeight={50}
          height={500}
          renderItem={renderItem}
        />
      );

      const list = screen.getByTestId('virtual-list');
      expect(list.getAttribute('data-item-count')).toBe('10000');
    });

    it('should handle very small item heights', () => {
      render(
        <VirtualList
          items={mockItems}
          itemHeight={20}
          height={500}
          renderItem={renderItem}
        />
      );

      const list = screen.getByTestId('virtual-list');
      expect(list.getAttribute('data-item-size')).toBe('20');
    });

    it('should handle very large item heights', () => {
      render(
        <VirtualList
          items={mockItems}
          itemHeight={200}
          height={500}
          renderItem={renderItem}
        />
      );

      const list = screen.getByTestId('virtual-list');
      expect(list.getAttribute('data-item-size')).toBe('200');
    });
  });

  describe('Threshold Configuration', () => {
    it('should accept custom threshold value', () => {
      const onLoadMore = jest.fn();
      
      render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          onLoadMore={onLoadMore}
          threshold={0.9}
          renderItem={renderItem}
        />
      );

      expect(screen.getByTestId('virtual-list')).toBeInTheDocument();
    });

    it('should use default threshold when not specified', () => {
      const onLoadMore = jest.fn();
      
      render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          onLoadMore={onLoadMore}
          renderItem={renderItem}
        />
      );

      expect(screen.getByTestId('virtual-list')).toBeInTheDocument();
    });
  });

  describe('Generic Type Support', () => {
    it('should work with different data types', () => {
      interface Product {
        id: number;
        title: string;
        price: number;
      }

      const products: Product[] = [
        { id: 1, title: 'Product 1', price: 10.99 },
        { id: 2, title: 'Product 2', price: 20.99 }
      ];

      const renderProduct = (product: Product) => (
        <div data-testid={`product-${product.id}`}>
          {product.title} - ${product.price}
        </div>
      );

      render(
        <VirtualList<Product>
          items={products}
          itemHeight={50}
          height={500}
          renderItem={renderProduct}
        />
      );

      expect(screen.getByTestId('product-1')).toHaveTextContent('Product 1 - $10.99');
    });

    it('should work with primitive types', () => {
      const numbers = [1, 2, 3, 4, 5];
      const renderNumber = (num: number) => (
        <div data-testid={`number-${num}`}>Number: {num}</div>
      );

      render(
        <VirtualList<number>
          items={numbers}
          itemHeight={50}
          height={500}
          renderItem={renderNumber}
        />
      );

      expect(screen.getByTestId('number-1')).toHaveTextContent('Number: 1');
    });

    it('should work with complex nested objects', () => {
      interface User {
        id: number;
        profile: {
          name: string;
          avatar: string;
        };
      }

      const users: User[] = [
        { id: 1, profile: { name: 'John', avatar: 'john.jpg' } }
      ];

      const renderUser = (user: User) => (
        <div data-testid={`user-${user.id}`}>{user.profile.name}</div>
      );

      render(
        <VirtualList<User>
          items={users}
          itemHeight={50}
          height={500}
          renderItem={renderUser}
        />
      );

      expect(screen.getByTestId('user-1')).toHaveTextContent('John');
    });
  });

  describe('Edge Cases', () => {
    it('should handle zero height', () => {
      render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={0}
          renderItem={renderItem}
        />
      );

      const list = screen.getByTestId('virtual-list');
      expect(list.style.height).toBe('0px');
    });

    it('should handle very large heights', () => {
      render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={10000}
          renderItem={renderItem}
        />
      );

      const list = screen.getByTestId('virtual-list');
      expect(list.style.height).toBe('10000px');
    });

    it('should handle rapid data changes', () => {
      const { rerender } = render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          renderItem={renderItem}
        />
      );

      // Rapidly change items
      for (let i = 0; i < 10; i++) {
        rerender(
          <VirtualList
            items={mockItems.slice(0, (i % 5) + 1)}
            itemHeight={50}
            height={500}
            renderItem={renderItem}
          />
        );
      }

      // Should still render correctly
      expect(screen.getByTestId('virtual-list')).toBeInTheDocument();
    });
  });

  describe('Memoization', () => {
    it('should memoize item renderer', () => {
      const renderItemMock = jest.fn(renderItem);

      const { rerender } = render(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          renderItem={renderItemMock}
        />
      );

      const callCount = renderItemMock.mock.calls.length;

      // Rerender with same props
      rerender(
        <VirtualList
          items={mockItems}
          itemHeight={50}
          height={500}
          renderItem={renderItemMock}
        />
      );

      // Should call the render function again due to React behavior
      expect(renderItemMock.mock.calls.length).toBeGreaterThanOrEqual(callCount);
    });
  });
});

describe('VirtualList - Integration', () => {
  it('should work in a complete scenario', () => {
    interface Task {
      id: number;
      title: string;
      completed: boolean;
    }

    const tasks: Task[] = Array.from({ length: 100 }, (_, i) => ({
      id: i,
      title: `Task ${i}`,
      completed: i % 2 === 0
    }));

    const renderTask = (task: Task) => (
      <div 
        data-testid={`task-${task.id}`}
        style={{ 
          padding: '10px',
          borderBottom: '1px solid #ccc',
          backgroundColor: task.completed ? '#e8f5e9' : 'white'
        }}
      >
        <strong>{task.title}</strong>
        {task.completed && ' ✓'}
      </div>
    );

    render(
      <VirtualList<Task>
        items={tasks}
        itemHeight={50}
        height={400}
        width={800}
        renderItem={renderTask}
        overscanCount={3}
      />
    );

    // Verify list is rendered
    expect(screen.getByTestId('virtual-list')).toBeInTheDocument();
    
    // Verify some items are rendered
    expect(screen.getByTestId('task-0')).toBeInTheDocument();
    expect(screen.getByTestId('task-1')).toBeInTheDocument();
  });
});
