# 👥 Team Guide: Enterprise Frontend Patterns

**Quick Reference for Developers**  
**Updated:** October 6, 2025

---

## 🚀 **Quick Start**

### **I need to create a form with validation:**
```typescript
import { useAdvancedForm } from '@/hooks/enterprise';
import type { IValidationRule } from '@/types/common';

interface MyFormData {
  name: string;
  email: string;
}

const validationRules: Partial<Record<keyof MyFormData, IValidationRule[]>> = {
  name: [{ type: 'required', message: 'Name required' }],
  email: [{ type: 'email', message: 'Invalid email' }],
};

const MyForm = () => {
  const form = useAdvancedForm<MyFormData>({
    initialValues: { name: '', email: '' },
    validationRules,
    onSubmit: async (values) => {
      await api.submit(values);
    }
  });

  return (
    <form onSubmit={form.handleSubmit()}>
      <TextField
        value={form.values.name}
        onChange={form.handleChange('name')}
        onBlur={form.handleBlur('name')}
        error={form.touched.name && Boolean(form.errors.name)}
        helperText={form.touched.name && form.errors.name}
      />
      <Button 
        type="submit"
        disabled={!form.isValid || form.isSubmitting}
      >
        {form.isSubmitting ? 'Saving...' : 'Submit'}
      </Button>
    </form>
  );
};
```

**See Examples:**
- `components/users/account-profile/Profile3/Profile.tsx`
- `components/users/account-profile/Profile1/ChangePassword.tsx`

---

### **I need to create a table with sorting, filtering, and pagination:**
```typescript
import { useTableLogic } from '@/hooks/useTableLogic';

const MyTable = () => {
  const table = useTableLogic<Customer>({
    data: customers,
    searchFields: ['name', 'email', 'location'],
    defaultOrderBy: 'name',
    defaultRowsPerPage: 10,
  });

  return (
    <div>
      <TextField
        value={table.search}
        onChange={table.handleSearch}
        placeholder="Search..."
      />
      
      <Table>
        <TableHead>
          {/* Use table.handleRequestSort for sorting */}
        </TableHead>
        <TableBody>
          {table.sortedAndPaginatedRows.map((row, index) => (
            <TableRow key={index}>
              <TableCell onClick={(e) => table.handleClick(e, row.name)}>
                <Checkbox checked={table.isSelected(row.name)} />
              </TableCell>
              {/* ... other cells */}
            </TableRow>
          ))}
        </TableBody>
      </Table>
      
      <TablePagination
        count={table.rows.length}
        page={table.page}
        rowsPerPage={table.rowsPerPage}
        onPageChange={table.handleChangePage}
        onRowsPerPageChange={table.handleChangeRowsPerPage}
      />
    </div>
  );
};
```

**See Examples:**
- `views/apps/customer/customer-list.tsx`
- `views/apps/customer/order-list.tsx`

---

### **I need to protect my component from errors:**
```typescript
import { withErrorBoundary } from '@/components/enterprise';

const MyComponent = () => {
  // Component code
};

// One line at the end!
export default withErrorBoundary(MyComponent);
```

**See Examples:**
- Any file in `views/apps/` (26+ examples)

---

## 📋 **Validation Rules Reference**

### **Common Validation Rules:**

```typescript
// Required field
{ type: 'required', message: 'This field is required' }

// Email validation
{ type: 'email', message: 'Please enter a valid email' }

// Minimum length
{ type: 'minLength', value: 8, message: 'Min 8 characters' }

// Maximum length
{ type: 'maxLength', value: 100, message: 'Max 100 characters' }

// Pattern matching (regex)
{ 
  type: 'pattern', 
  value: /^\d{3}-\d{3}-\d{4}$/, 
  message: 'Format: 123-456-7890' 
}

// Custom validation
{ 
  type: 'custom',
  validator: (value, formValues) => value === formValues?.password,
  message: 'Passwords must match'
}
```

### **Password Validation Example:**
```typescript
validationRules: {
  password: [
    { type: 'required', message: 'Password required' },
    { type: 'minLength', value: 8, message: 'Min 8 characters' },
    {
      type: 'pattern',
      value: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]/,
      message: 'Must contain uppercase, lowercase, number & special character'
    }
  ],
  confirmPassword: [
    { type: 'required', message: 'Please confirm password' },
    {
      type: 'custom',
      validator: (value, formValues) => value === formValues?.password,
      message: 'Passwords must match'
    }
  ]
}
```

---

## 🎯 **Common Scenarios**

### **Scenario 1: User Profile Form**
**Pattern:** useAdvancedForm  
**Example:** `Profile3/Profile.tsx`  
**Time to implement:** 15-20 minutes

### **Scenario 2: Data Table with Search**
**Pattern:** useTableLogic<T>  
**Example:** `customer-list.tsx`  
**Time to implement:** 20-30 minutes

### **Scenario 3: Page Component**
**Pattern:** withErrorBoundary  
**Example:** Any `/views/apps` page  
**Time to implement:** 1 minute

### **Scenario 4: Password Change Form**
**Pattern:** useAdvancedForm with custom validator  
**Example:** `Profile1/ChangePassword.tsx`  
**Time to implement:** 20-25 minutes

---

## 🔍 **Troubleshooting**

### **Q: Form validation not working?**
**A:** Make sure you're using `onChange={form.handleChange('field')}` not just `onChange={form.handleChange}`

### **Q: Table not sorting?**
**A:** Use `table.sortedAndPaginatedRows` not `table.rows`

### **Q: Error boundary not catching errors?**
**A:** Error boundaries only catch errors in child components, not in the same component

### **Q: Form always disabled?**
**A:** Check if form.isValid is false. Log form.errors to see validation issues.

---

## 📚 **Additional Resources**

### **Documentation:**
- Hooks: `frontend/src/hooks/README.md`
- Enterprise Components: `frontend/src/components/enterprise/README.md`

### **Examples:**
- Forms: All Profile components in `components/users/account-profile/`
- Tables: `views/apps/customer/customer-list.tsx`, `order-list.tsx`
- Error Boundaries: Any `/views/apps` page

---

## 🎓 **Team Training Checklist**

- [ ] Review useAdvancedForm hook documentation
- [ ] Review useTableLogic<T> hook documentation
- [ ] Try implementing a simple form with validation
- [ ] Try converting a table to use useTableLogic
- [ ] Apply withErrorBoundary to a component
- [ ] Review modernized examples in codebase

---

**Questions?** Check the README files in `frontend/src/hooks/` and `frontend/src/components/enterprise/`
