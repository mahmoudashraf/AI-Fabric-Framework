"use client";
import React from 'react';
import { Box, Button, Avatar } from '@mui/material';
import MainCard from 'ui-component/cards/MainCard';
import { DataProvider, VirtualList } from '@/components/enterprise';
import type { ApiResponse } from '@/types/enterprise';

type User = { id: string; name: string; email: string };

async function fetchUsers(): Promise<ApiResponse<User[]>> {
  // Simulated API call for demo purposes
  await new Promise((r) => setTimeout(r, 300));
  const users: User[] = Array.from({ length: 50 }).map((_, i) => ({
    id: String(i + 1),
    name: `User ${i + 1}`,
    email: `user${i + 1}@example.com`
  }));
  return {
    data: users,
    message: 'Success',
    success: true,
    statusCode: 200,
    timestamp: new Date().toISOString(),
    requestId: 'demo-req'
  };
}

export default function EnterpriseExamplesPage() {
  return (
    <Box sx={{ p: 2, display: 'grid', gap: 2 }}>
      <MainCard>
        <MainCard.Header title="Enterprise Components" />
        <MainCard.Body>
          <DataProvider<User[]> fetchFn={fetchUsers}>
            {({ data, loading, error, refetch }) => (
              <Box sx={{ display: 'grid', gap: 2 }}>
                {error && <div>Error: {error.message}</div>}
                <Button variant="contained" onClick={refetch} disabled={loading}>
                  {loading ? 'Loading...' : 'Refetch'}
                </Button>
                <div>
                  <VirtualList
                    items={data || []}
                    itemHeight={64}
                    height={320}
                    loading={loading}
                    renderItem={(user) => (
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, p: 1 }}>
                        <Avatar>{user.name.charAt(0)}</Avatar>
                        <div>
                          <div>{user.name}</div>
                          <div style={{ opacity: 0.7 }}>{user.email}</div>
                        </div>
                      </Box>
                    )}
                  />
                </div>
              </Box>
            )}
          </DataProvider>
        </MainCard.Body>
        <MainCard.Actions justifyContent="center">
          <Button variant="outlined">Primary Action</Button>
        </MainCard.Actions>
      </MainCard>
    </Box>
  );
}


