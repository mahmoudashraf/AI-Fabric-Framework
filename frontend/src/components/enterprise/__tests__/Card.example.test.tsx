import React from 'react';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test-utils/enterprise-testing';
import MainCard from 'ui-component/cards/MainCard';

describe('Card (example test)', () => {
  it('renders header and body content', () => {
    renderWithProviders(
      <MainCard>
        <MainCard.Header title="Example Card" />
        <MainCard.Body>
          <div data-testid="content">Hello</div>
        </MainCard.Body>
      </MainCard>
    );
    expect(screen.getByText('Example Card')).toBeInTheDocument();
    expect(screen.getByTestId('content')).toBeInTheDocument();
  });
});


