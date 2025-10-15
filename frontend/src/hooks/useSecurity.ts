'use client';

import { useEffect, useState, useCallback } from 'react';

// Security utilities
export class SecurityUtils {
  /**
   * Sanitize HTML content to prevent XSS attacks
   */
  static sanitizeHtml(html: string): string {
    const div = document.createElement('div');
    div.textContent = html;
    return div.innerHTML;
  }

  /**
   * Validate email format
   */
  static isValidEmail(email: string): boolean {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  }

  /**
   * Validate password strength
   */
  static validatePassword(password: string): {
    isValid: boolean;
    score: number;
    feedback: string[];
  } {
    const feedback: string[] = [];
    let score = 0;

    if (password.length < 8) {
      feedback.push('Password must be at least 8 characters long');
    } else {
      score += 1;
    }

    if (!/[A-Z]/.test(password)) {
      feedback.push('Password must contain at least one uppercase letter');
    } else {
      score += 1;
    }

    if (!/[a-z]/.test(password)) {
      feedback.push('Password must contain at least one lowercase letter');
    } else {
      score += 1;
    }

    if (!/\d/.test(password)) {
      feedback.push('Password must contain at least one number');
    } else {
      score += 1;
    }

    if (!/[!@#$%^&*(),.?":{}|<>]/.test(password)) {
      feedback.push('Password must contain at least one special character');
    } else {
      score += 1;
    }

    return {
      isValid: score >= 4,
      score,
      feedback,
    };
  }

  /**
   * Generate secure random token
   */
  static generateSecureToken(length: number = 32): string {
    const array = new Uint8Array(length);
    crypto.getRandomValues(array);
    return Array.from(array, byte => byte.toString(16).padStart(2, '0')).join('');
  }

  /**
   * Hash password using Web Crypto API
   */
  static async hashPassword(password: string): Promise<string> {
    const encoder = new TextEncoder();
    const data = encoder.encode(password);
    const hashBuffer = await crypto.subtle.digest('SHA-256', data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
  }

  /**
   * Validate URL to prevent open redirect attacks
   */
  static isValidUrl(url: string, allowedDomains?: string[]): boolean {
    try {
      const urlObj = new URL(url);

      if (allowedDomains) {
        return allowedDomains.includes(urlObj.hostname);
      }

      // Only allow http/https protocols
      return ['http:', 'https:'].includes(urlObj.protocol);
    } catch {
      return false;
    }
  }

  /**
   * Escape special characters for SQL injection prevention
   */
  static escapeSqlString(str: string): string {
    return str.replace(/'/g, "''").replace(/\\/g, '\\\\');
  }

  /**
   * Validate file upload
   */
  static validateFileUpload(
    file: File,
    options: {
      maxSize?: number;
      allowedTypes?: string[];
      allowedExtensions?: string[];
    } = {}
  ): { isValid: boolean; error?: string } {
    const { maxSize = 10 * 1024 * 1024, allowedTypes = [], allowedExtensions = [] } = options;

    if (file.size > maxSize) {
      return { isValid: false, error: 'File size exceeds maximum allowed size' };
    }

    if (allowedTypes.length > 0 && !allowedTypes.includes(file.type)) {
      return { isValid: false, error: 'File type not allowed' };
    }

    if (allowedExtensions.length > 0) {
      const extension = file.name.split('.').pop()?.toLowerCase();
      if (!extension || !allowedExtensions.includes(extension)) {
        return { isValid: false, error: 'File extension not allowed' };
      }
    }

    return { isValid: true };
  }
}

// Hook for input sanitization
export function useInputSanitization() {
  const sanitizeInput = useCallback((input: string): string => {
    return SecurityUtils.sanitizeHtml(input);
  }, []);

  const validateEmail = useCallback((email: string): boolean => {
    return SecurityUtils.isValidEmail(email);
  }, []);

  const validatePassword = useCallback((password: string) => {
    return SecurityUtils.validatePassword(password);
  }, []);

  return {
    sanitizeInput,
    validateEmail,
    validatePassword,
  };
}

// Hook for CSRF protection
export function useCSRFProtection() {
  const [csrfToken, setCsrfToken] = useState<string | null>(null);

  useEffect(() => {
    // Generate CSRF token on component mount
    const token = SecurityUtils.generateSecureToken();
    setCsrfToken(token);

    // Store token in session storage
    sessionStorage.setItem('csrf-token', token);
  }, []);

  const getCSRFToken = useCallback((): string | null => {
    return csrfToken || sessionStorage.getItem('csrf-token');
  }, [csrfToken]);

  const validateCSRFToken = useCallback(
    (token: string): boolean => {
      const storedToken = getCSRFToken();
      return storedToken === token;
    },
    [getCSRFToken]
  );

  return {
    csrfToken,
    getCSRFToken,
    validateCSRFToken,
  };
}

// Hook for rate limiting
export function useRateLimit(maxRequests: number = 10, windowMs: number = 60000) {
  const [requests, setRequests] = useState<number[]>([]);

  const isAllowed = useCallback((): boolean => {
    const now = Date.now();
    const windowStart = now - windowMs;

    // Remove old requests outside the window
    const validRequests = requests.filter(timestamp => timestamp > windowStart);
    setRequests(validRequests);

    // Check if we're under the limit
    if (validRequests.length >= maxRequests) {
      return false;
    }

    // Add current request
    setRequests(prev => [...prev, now]);
    return true;
  }, [requests, maxRequests, windowMs]);

  const reset = useCallback(() => {
    setRequests([]);
  }, []);

  return {
    isAllowed,
    reset,
    remainingRequests: Math.max(0, maxRequests - requests.length),
  };
}

// Hook for secure file upload
export function useSecureFileUpload(
  options: {
    maxSize?: number;
    allowedTypes?: string[];
    allowedExtensions?: string[];
  } = {}
) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const uploadFile = useCallback(
    async (file: File): Promise<string | null> => {
      setUploading(true);
      setError(null);

      try {
        // Validate file
        const validation = SecurityUtils.validateFileUpload(file, options);
        if (!validation.isValid) {
          throw new Error(validation.error);
        }

        // Create FormData
        const formData = new FormData();
        formData.append('file', file);

        // Add CSRF token if available
        const csrfToken = sessionStorage.getItem('csrf-token');
        if (csrfToken) {
          formData.append('csrf-token', csrfToken);
        }

        // Upload file
        const response = await fetch('/api/upload', {
          method: 'POST',
          body: formData,
        });

        if (!response.ok) {
          throw new Error('Upload failed');
        }

        const result = await response.json();
        return result.url;
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Upload failed';
        setError(errorMessage);
        return null;
      } finally {
        setUploading(false);
      }
    },
    [options]
  );

  return {
    uploadFile,
    uploading,
    error,
  };
}

// Hook for content security policy
export function useContentSecurityPolicy() {
  const [cspViolations, setCspViolations] = useState<string[]>([]);

  useEffect(() => {
    const handleCSPViolation = (event: SecurityPolicyViolationEvent) => {
      console.warn('CSP Violation:', event);
      setCspViolations(prev => [...prev, event.violatedDirective]);
    };

    document.addEventListener('securitypolicyviolation', handleCSPViolation);

    return () => {
      document.removeEventListener('securitypolicyviolation', handleCSPViolation);
    };
  }, []);

  return {
    cspViolations,
    clearViolations: () => setCspViolations([]),
  };
}

// Hook for secure storage
export function useSecureStorage() {
  const setSecureItem = useCallback((key: string, value: string): void => {
    try {
      // Encrypt sensitive data before storing
      const encryptedValue = btoa(value); // Simple base64 encoding for demo
      localStorage.setItem(key, encryptedValue);
    } catch (error) {
      console.error('Failed to store secure item:', error);
    }
  }, []);

  const getSecureItem = useCallback((key: string): string | null => {
    try {
      const encryptedValue = localStorage.getItem(key);
      if (!encryptedValue) return null;

      // Decrypt data after retrieving
      return atob(encryptedValue); // Simple base64 decoding for demo
    } catch (error) {
      console.error('Failed to retrieve secure item:', error);
      return null;
    }
  }, []);

  const removeSecureItem = useCallback((key: string): void => {
    localStorage.removeItem(key);
  }, []);

  return {
    setSecureItem,
    getSecureItem,
    removeSecureItem,
  };
}
