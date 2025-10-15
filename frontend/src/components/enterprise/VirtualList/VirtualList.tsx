import React, { useState, useEffect, useRef, useCallback } from 'react';
import { FixedSizeList as List } from 'react-window';
import { Box, Skeleton } from '@mui/material';

export interface VirtualListProps<T> {
  items: T[];
  itemHeight: number;
  height: number;
  width?: number;
  renderItem: (item: T, index: number) => React.ReactNode;
  loading?: boolean;
  onLoadMore?: () => void;
  threshold?: number;
  overscanCount?: number;
}

export function VirtualList<T>({
  items,
  itemHeight,
  height,
  width = 600,
  renderItem,
  loading = false,
  onLoadMore,
  threshold = 0.8,
  overscanCount = 5
}: VirtualListProps<T>) {
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const listRef = useRef<List>(null);

  const ItemRenderer = useCallback(({ index, style }: { index: number; style: React.CSSProperties }) => {
    const item = items[index];
    if (!item) {
      return (
        <div style={style}>
          <Skeleton variant="rectangular" height={itemHeight} />
        </div>
      );
    }
    return <div style={style}>{renderItem(item, index)}</div>;
  }, [items, renderItem, itemHeight]);

  const handleScroll = useCallback(({ scrollTop, scrollHeight, clientHeight }: any) => {
    if (!onLoadMore || isLoadingMore) return;
    const scrollPercentage = (scrollTop + clientHeight) / scrollHeight;
    if (scrollPercentage >= threshold) {
      setIsLoadingMore(true);
      onLoadMore();
    }
  }, [onLoadMore, isLoadingMore, threshold]);

  useEffect(() => {
    if (isLoadingMore) {
      setIsLoadingMore(false);
    }
  }, [items.length, isLoadingMore]);

  return (
    <Box sx={{ height, width: '100%' }}>
      <List
        ref={listRef}
        height={height}
        width={width}
        itemCount={items.length + (loading ? 1 : 0)}
        itemSize={itemHeight}
        onScroll={handleScroll}
        overscanCount={overscanCount}
      >
        {ItemRenderer}
      </List>
    </Box>
  );
}


