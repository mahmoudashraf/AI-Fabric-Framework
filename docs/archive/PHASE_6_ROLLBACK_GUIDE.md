# Phase 6 Rollback Quick Reference Guide

## Quick Rollback Commands

### 1. Remove All Phase 6 Files
```bash
# Navigate to frontend directory
cd /Users/mahmoudashraf/Downloads/easy-luxury/frontend

# Remove performance components
rm -rf src/components/enterprise/performance/

# Remove chart wrapper
rm -rf src/components/ui-component/charts/

# Remove optimized views
rm -f src/views/widget/statistics-optimized.tsx
rm -f src/views/widget/data-optimized.tsx
rm -f src/views/dashboard/default-optimized.tsx
rm -f src/views/performance-comparison.tsx

# Remove optimized pages
rm -rf src/app/\(dashboard\)/widget/statistics-optimized/
rm -rf src/app/\(dashboard\)/widget/data-optimized/
rm -rf src/app/\(dashboard\)/dashboard/default-optimized/
rm -rf src/app/\(dashboard\)/performance-comparison/
```

### 2. Restore Original Chart Components
```bash
# Restore chart components to original state (if you have git history)
git checkout HEAD~1 -- src/components/dashboard/Default/TotalGrowthBarChart.tsx
git checkout HEAD~1 -- src/components/dashboard/Default/TotalOrderLineChartCard.tsx
git checkout HEAD~1 -- src/components/dashboard/Default/BajajAreaChartCard.tsx
```

### 3. Restore Menu Files
```bash
# Restore menu files to original state
git checkout HEAD~1 -- src/menu-items/widget.tsx
git checkout HEAD~1 -- src/menu-items/index.tsx
git checkout HEAD~1 -- src/layout/MainLayout/MenuList/index.tsx
```

### 4. Verify Rollback
```bash
# Run type check
npm run type-check

# Start development server
npm run dev

# Test dashboard
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/dashboard/default
```

## Manual Restoration (If Git History Not Available)

### TotalGrowthBarChart.tsx
Restore to use direct `react-apexcharts` import:
```typescript
import dynamic from 'next/dynamic';
import { useEffect, useState } from 'react';

const ReactApexChart = dynamic(() => import('react-apexcharts'), { ssr: false });

// Use ReactApexChart directly in render
<ReactApexChart options={options} series={series} type="bar" height={480} />
```

### TotalOrderLineChartCard.tsx
Restore to use direct `react-apexcharts` import:
```typescript
import dynamic from 'next/dynamic';
import { useEffect, useState } from 'react';

const ReactApexChart = dynamic(() => import('react-apexcharts'), { ssr: false });

// Use ReactApexChart directly in render
<ReactApexChart options={chartOptions} series={series} type="line" height={90} />
```

### BajajAreaChartCard.tsx
Restore to use direct `react-apexcharts` import:
```typescript
import dynamic from 'next/dynamic';
import { useEffect, useState } from 'react';

const ReactApexChart = dynamic(() => import('react-apexcharts'), { 
  ssr: false,
  loading: () => <div>Loading chart...</div>
});

// Use ReactApexChart directly in render
<ReactApexChart options={options} series={series} type="area" height={95} />
```

### widget.tsx Menu
Restore to original function-based menu:
```typescript
import { useMenu } from 'contexts/MenuContext';

export const Menu = () => {
  const { menuOrientation } = useMenu();
  
  return {
    items: [
      {
        id: 'statistics',
        title: 'Statistics',
        type: 'item',
        url: '/widget/statistics',
        icon: 'IconChartArcs',
        breadcrumbs: false,
      },
      // ... other menu items
    ]
  };
};
```

### menu-items/index.tsx
Remove widgetMenu import:
```typescript
// Remove this line:
// import widgetMenu from './widget';

const menuItems: { items: NavItemType[] } = {
  items: [dashboard, application, forms, elements, samplePage, pages, utilities, support, other], // Remove widgetMenu
};
```

### MenuList/index.tsx
Restore original imports:
```typescript
// Remove this line:
// import widgetMenu from 'menu-items/widget';

// Remove this line:
// let getMenu = widgetMenu;
```

## Post-Rollback Checklist

- [ ] All Phase 6 files removed
- [ ] Chart components restored to original state
- [ ] Menu system restored to original state
- [ ] TypeScript type-check passes
- [ ] Development server starts without errors
- [ ] Dashboard loads successfully
- [ ] No console errors in browser

## Notes

- The rollback will restore Next.js 15 compatibility warnings
- Chart components will work but may show console warnings
- Performance optimizations will be removed
- All Phase 6 features will be unavailable

## Re-implementation

To re-implement Phase 6, refer to the comprehensive documentation:
`PHASE_6_COMPREHENSIVE_DOCUMENTATION.md`
