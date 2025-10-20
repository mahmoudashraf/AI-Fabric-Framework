import React from 'react';
import AdvancedSearch from '../../../components/ai/AdvancedSearch';

const AdvancedSearchPage: React.FC = () => {
  return (
    <AdvancedSearch
      onSearch={(query, options) => {
        console.log('Search performed:', { query, options });
      }}
      onResultClick={(result) => {
        console.log('Result clicked:', result);
      }}
      showAdvancedOptions={true}
      maxResults={10}
    />
  );
};

export default AdvancedSearchPage;