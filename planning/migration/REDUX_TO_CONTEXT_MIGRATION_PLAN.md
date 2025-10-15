# PLAN — Redux to Context API + React Query Migration

## 0) Summary
- **Goal**: Gradually migrate away from Redux Toolkit to React Context API + React Query with minimal disruption
- **Strategy**: Tiny incremental changes following project guidelines; maintain functionality during transition
- **Non-goals**: Immediate complete removal, breaking existing features, large refactoring changes
- **Timeline**: 8-12 weeks across multiple small PR-sized batches

## 1) Migration Strategy

### Phase-by-Phase Approach
```
Phase 1: Foundation Setup (Week 1-2)
Phase 2: Simple State Migration (Week 3-4) 
Phase 3: Complex State Migration (Week 5-8)
Phase 4: Cleanup & Optimization (Week 9-10)
Phase 5: Final Removal (Week 11-12)
```

### Migration Principles
- **Zero Breaking Changes**: Keep Redux functional until replacement is fully tested
- **Incremental Migration**: One slice/context per PR
- **Parallel Operation**: Both systems run side-by-side during transition
- **Feature Flags**: Control which system is active per feature
- **Rollback Ready**: Each change can be reverted independently

## 2) Current Redux State Analysis

### Identified Redux Slices (from store/slices/)
- `snackbar.slice.ts` → Simple notification state
- `cart.slice.ts` → Shopping cart (keep as Context, migrate to React Query when needed)
- `kanban.slice.ts` → Complex board state (evaluate for migration vs keep as Context)
- `customization.slice.ts` → Theme/layout preferences
- `auth.slice.ts` → Authentication state
- `other slices` → To be analyzed during migration

### Migration Priority Matrix
| Slice | Complexity | Depends On | Priority | Target Migration |
|-------|------------|------------|----------|------------------|
| snackbar | Low | None | 1 | Context API |
| customization | Low | None | 2 | Context API |
| auth | Medium | External APIs | 3 | React Query |
| cart | Medium | Product data | 4 | Context + React Query |
| kanban | High | Multiple entities | 5 | Evaluate case-by-case |

## 3) Technical Implementation Plan

### Phase 1: Foundation Setup (Week 1-2)

#### Batch 1.1: Setup React Query Foundation
**Goal**: Establish React Query infrastructure
**Components**:
- Install `@tanstack/react-query` (latest version)
- Create `src/lib/react-query.ts` - Query client configuration
- Create `src/providers/ReactQueryProvider.tsx` - Provider wrapper
- Update `src/layout/index.tsx` - Add React Query provider
- Create `src/hooks/useReactQuery.ts` - Common query patterns

#### Batch 1.2: Context API Infrastructure
**Goal**: Create context patterns for simple state
**Components**:
- Create `src/contexts/NotificationContext.tsx` - Future snackbar replacement
- Create `src/contexts/ThemeContext.tsx` - Theme customization
- Create `src/contexts/AuthContext.tsx` - Authentication state
- Create `src/hooks/useContext.ts` - Custom context hooks

**Example Implementation**:
```typescript
// src/contexts/NotificationContext.tsx
'use client';

import React, { createContext, useContext, useReducer } from 'react';

interface NotificationState {
  notifications: Notification[];
}

const NotificationContext = createContext<{
  state: NotificationState;
  dispatch: React.Dispatch<NotificationAction>;
} | null>(null);

export const NotificationProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [state, dispatch] = useReducer(notificationReducer, initialState);
  
  return (
    <NotificationContext.Provider value={{ state, dispatch }}>
      {children}
    </NotificationContext.Provider>
  );
};

export const useNotifications = () => {
  const context = useContext(NotificationContext);
  if (!context) throw new Error('useNotifications must be used within NotificationProvider');
  return context;
};
```

#### Batch 1.3: Migration Utilities
**Goal**: Create migration tools and comparison utilities
**Components**:
- Create `src/utils/reduxComparison.ts` - Side-by-side state comparison
- Create `src/debug/stateInspector.tsx` - Development state inspector
- Create migration test utilities

### Phase 2: Simple State Migration (Week 3-4)

#### Batch 2.1: Snackbar Migration
**Goal**: Migrate snackbar state from Redux to Context
**Implementation**:
- Extend `NotificationContext` to handle snackbar notifications
- Create `useSnackbar` hook with same API as Redux
- Add migration comparison tests
- **Feature Flag**: `FEATURES.MIGRATED_NOTIFICATIONS`

**Usage Migration**:
```typescript
// Before (Redux)
const dispatch = useDispatch();
dispatch(openSnackbar({ message: 'Success!' }));

// After (Context) - Same API
const { showSnackbar } = useSnackbar();
showSnackbar('Success!');
```

#### Batch 2.2: Theme Customization Migration
**Goal**: Migrate theme/layout customization from Redux to Context
**Components**:
- Enhance `ThemeContext` to handle all customization features
- Migrate theme selector components
- Preserve localStorage persistence
- **Feature Flag**: `FEATURES.MIGRATED_THEME`

### Phase 3: Complex State Migration (Week 5-8)

#### Batch 3.1: Auth State Migration
**Goal**: Migrate authentication to React Query + Context
**Strategy**:
- Keep auth endpoints in React Query
- Use Context for UI state (loading, error, user info)
- Maintain session persistence
- **Tests**: Ensure auth flows work identically

#### Batch 3.2: Shopping Cart Migration
**Goal**: Migrate cart functionality
**Components**:
- React Query for cart operations (add/remove/update)
- Context for cart UI state
- Local storage optimization layer
- **Consideration**: Whether to keep cart in Context or move fully to React Query

#### Batch 3.3: Complex State Evaluation
**Goal**: Evaluate kanban and other complex slices
**Decision Matrix**:
- If state is mostly UI-driven → Context API
- If state requires server sync → React Query
- If state is highly interactive → Consider keeping as Context
- If state has complex relationships → Evaluate React Query mutations

### Phase 4: Cleanup & Optimization (Week 9-10)

#### Batch 4.1: Performance Optimization
**Goals**:
- Optimize React Query revalidation strategies
- Minimize context re-renders
- Bundle size analysis and optimization
- Performance monitoring setup

#### Batch 4.2: Testing & Documentation
**Components**:
- Complete test coverage for new patterns
- Update documentation
- Migration guide for developers
- Performance benchmarks

### Phase 5: Final Removal (Week 11-12)

#### Batch 5.1: Redux Cleanup
**Components**:
- Remove Redux Toolkit dependencies
- Clean up store configuration
- Remove unused Redux slices
- Update build configuration

#### Batch 5.2: Final Testing
**Components**:
- Complete integration testing
- Performance validation
- User acceptance testing
- Documentation updates

## 4) Migration Patterns

### Pattern 1: Simple State → Context API
```typescript
// Redux Pattern
const slice = createSlice({
  name: 'simpleState',
  initialState,
  reducers: { /* ... */ }
});

// Context Pattern
const Context = createContext<StateValue | null>(null);
const Provider = ({ children }) => {
  const [state, dispatch] = useReducer(reducer, initialState);
  return <Context.Provider value={{ state, dispatch }}>{children}</Context.Provider>;
};
```

### Pattern 2: Server State → React Query
```typescript
// Redux Pattern (with thunks)
const fetchDataAction = createAsyncThunk('data/fetch', async () => {
  const response = await api.getData();
  return response.data;
});

// React Query Pattern
const useData = () => {
  return useQuery({
    queryKey: ['data'],
    queryFn: () => api.getData(),
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};
```

### Pattern 3: Hybrid Approach
```typescript
// Combine React Query + Context for complex scenarios
const UserContext = createContext<UserContextType>({});
const UserProvider = ({ children }) => {
  const { data: user, isLoading } = useQuery({
    queryKey: ['user', 'current'],
    queryFn: () => getCurrentUser(),
  });
  
  const [uiState, setUiState] = useState({ theme: 'light' });
  
  return (
    <UserContext.Provider value={{ user, isLoading, uiState, setUiState }}>
      {children}
    </UserContext.Provider>
  );
};
```

## 5) Testing Strategy

### Migration Testing Approach
```typescript
// Side-by-side comparison helper
const useReduxComparison = <T>(reduxValue: T, contextValue: T) => {
  useEffect(() => {
    if (process.env.NODE_ENV === 'development') {
      if (JSON.stringify(reduxValue) !== JSON.stringify(contextValue)) {
        console.warn('State mismatch detected:', { reduxValue, contextValue });
      }
    }
  }, [reduxValue, contextValue]);
};
```

### Test Categories
1. **Unit Tests**: Individual context providers and React Query hooks
2. **Integration Tests**: Feature-level testing with new patterns
3. **Performance Tests**: Re-render counts, bundle size, load times
4. **Migration Tests**: Side-by-side comparison during transition

## 6) Risk Mitigation

### Technical Risks
- **Breaking Changes**: Feature flags and gradual rollout
- **Performance Regression**: Continuous monitoring and benchmarking
- **Developer Confusion**: Clear documentation and migration guides
- **State Inconsistency**: Comparison utilities and thorough testing

### Mitigation Strategies
- **Feature Flags**: Each migration behind feature flags
- **A/B Testing**: Optional A/B testing for critical features
- **Monitoring**: Enhanced logging during migration period
- **Rollback Plans**: Clear rollback procedures for each batch

## 7) Success Metrics

### Performance Metrics
- Bundle size reduction (target: 15-20%)
- Initial load time improvement
- Runtime performance (re-render counts)
- Memory usage optimization

### Developer Experience
- Reduced boilerplate code
- Improved TypeScript support
- Simplified testing patterns
- Better debugging capabilities

### Functionality Preservation
- All features continue to work identically
- User experience remains unchanged
- Existing integrations continue to function

## 8) Implementation Details

### Required Dependencies
```json
{
  "dependencies": {
    "@tanstack/react-query": "^5.0.0",
    "@tanstack/react-query-devtools": "^5.0.0"
  },
  "devDependencies": {
    "@types/react": "^18.0.0"
  }
}
```

### File Structure Changes
```
src/
├── contexts/                 # New Context providers
│   ├── NotificationContext.tsx
│   ├── ThemeContext.tsx
│   ├── AuthContext.tsx
│   └── index.ts
├── hooks/
│   ├── useReactQuery.ts      # React Query utilities
│   ├── useNotification.ts    # Context-based hooks
│   ├── useAuth.ts           # Updated auth hook
│   └── useTheme.ts          # Theme hook
├── lib/
│   ├── react-query.ts       # Query client config
│   └── store-comparison.ts  # Migration utilities
└── store/                   # Gradually deprecated
    ├── slices/             # To be removed
    └── index.ts            # To be simplified
```

## 9) Acceptance Criteria

### Batch Completion Criteria
Each batch must meet:
- [ ] Feature functionality preserved (no regressions)
- [ ] Performance metrics maintained or improved
- [ ] Test coverage ≥ previous level
- [ ] Documentation updated
- [ ] Code review approved
- [ ] Feature flag controls migration

### Overall Success Criteria
- [ ] Redux completely removed from codebase
- [ ] Bundle size reduced by 15-20%
- [ ] All tests passing
- [ ] No functional regressions
- [ ] Developer experience improved
- [ ] Performance maintained or improved

## 10) Rollback Plans

### Batch-Specific Rollbacks
Each batch includes:
- Feature flag to disable new implementation
- Automated tests to verify rollback
- Clear rollback documentation
- Database/state migration reversibility

### Emergency Rollback
- Complete Redux restoration capability
- Automated testing for rollback
- Monitoring for issues
- Communication plan for stakeholders

This migration plan ensures a safe, gradual transition from Redux to React Query + Context API while maintaining all functionality and improving developer experience.
