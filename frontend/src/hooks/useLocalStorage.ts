import { useState, useEffect } from 'react';

// ==============================|| LOCAL STORAGE HOOKS ||============================== //

export default function useLocalStorage<ValueType>(key: string, defaultValue: ValueType): [
  ValueType,
  (newValue: ValueType | ((prev: ValueType) => ValueType)) => void
] {
  const [value, setValue] = useState<ValueType>(defaultValue);
  
  // Initialize from localStorage on client side only
  useEffect(() => {
    if (typeof window !== 'undefined') {
      try {
        const storedValue = localStorage.getItem(key);
        if (storedValue !== null) {
          setValue(JSON.parse(storedValue));
        }
      } catch (error) {
        console.warn(`Error reading from localStorage key "${key}":`, error);
      }
    }
  }, [key]);

  useEffect(() => {
    const listener = (e: StorageEvent) => {
      if (e.storageArea === localStorage && e.key === key) {
        setValue(e.newValue ? JSON.parse(e.newValue) : e.newValue);
      }
    };
    window.addEventListener('storage', listener);

    return () => {
      window.removeEventListener('storage', listener);
    };
  }, [key, defaultValue]);

  const setValueInLocalStorage = (newValue: ValueType | ((prev: ValueType) => ValueType)) => {
    setValue((currentValue: any) => {
      const result = typeof newValue === 'function' ? (newValue as any)(currentValue) : newValue;
      if (typeof window !== 'undefined') {
        localStorage.setItem(key, JSON.stringify(result));
      }
      return result;
    });
  };

  return [value, setValueInLocalStorage];
}
