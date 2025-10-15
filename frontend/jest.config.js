const nextJest = require('next/jest');

const createJestConfig = nextJest({
  dir: './',
});

const customJestConfig = {
  setupFilesAfterEnv: ['<rootDir>/jest.setup.js'],
  testEnvironment: 'jsdom',
  testPathIgnorePatterns: ['<rootDir>/.next/', '<rootDir>/node_modules/'],
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
    '^ui-component/(.*)$': '<rootDir>/src/components/ui-component/$1',
    '^@mui/material/styles': '<rootDir>/node_modules/@mui/material/styles',
    '^@mui/material/(.*)$': '<rootDir>/node_modules/@mui/material/$1',
    '^@mui/icons-material/(.*)$': '<rootDir>/node_modules/@mui/icons-material/$1',
  },
  collectCoverageFrom: [
    'src/components/enterprise/**/*.{js,jsx,ts,tsx}',
    'src/hooks/enterprise/**/*.{js,jsx,ts,tsx}',
    'src/test-utils/**/*.{js,jsx,ts,tsx}'
  ],
  coverageThreshold: {
    global: {
      branches: 0,
      functions: 0,
      lines: 0,
      statements: 0,
    },
  },
  testMatch: [
    '<rootDir>/src/components/enterprise/**/__tests__/**/*.{js,jsx,ts,tsx}',
    '<rootDir>/src/hooks/enterprise/**/__tests__/**/*.{js,jsx,ts,tsx}',
  ],
  testPathIgnorePatterns: ['<rootDir>/.next/', '<rootDir>/node_modules/'],
  transform: {
    '^.+\\.(js|jsx|ts|tsx)$': ['babel-jest', { presets: ['next/babel'] }],
  },
  transformIgnorePatterns: ['/node_modules/', '^.+\\.module\\.(css|sass|scss)$'],
  moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json', 'node'],
};

module.exports = createJestConfig(customJestConfig);
