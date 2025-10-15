'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Box,
  Button,
  Checkbox,
  FormControl,
  FormControlLabel,
  FormHelperText,
  IconButton,
  InputAdornment,
  InputLabel,
  OutlinedInput,
  Stack,
  Typography,
  Alert,
  Grid,
} from '@mui/material';
import { Visibility, VisibilityOff } from '@mui/icons-material';
import { useSupabaseAuth } from '@/contexts/SupabaseAuthContext';
import { DASHBOARD_PATH } from '@/config';
import SocialLoginButtons from './SocialLoginButtons';

const AuthSupabaseRegister = () => {
  const router = useRouter();
  const { signUp } = useSupabaseAuth();
  
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
  });
  const [showPassword, setShowPassword] = useState(false);
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  const validateForm = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (!formData.firstName.trim()) {
      newErrors.firstName = 'First name is required';
    }

    if (!formData.lastName.trim()) {
      newErrors.lastName = 'Last name is required';
    }

    if (!formData.email) {
      newErrors.email = 'Email is required';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
      newErrors.email = 'Please enter a valid email address';
    } else if (formData.email.length > 254) {
      newErrors.email = 'Email address is too long';
    }

    if (!formData.password) {
      newErrors.password = 'Password is required';
    } else if (formData.password.length < 6) {
      newErrors.password = 'Password must be at least 6 characters';
    }

    if (!agreeTerms) {
      newErrors.terms = 'You must agree to the terms and conditions';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrors({});

    if (!validateForm()) {
      return;
    }

    setIsSubmitting(true);

    try {
      await signUp(formData.email, formData.password);
      router.push(DASHBOARD_PATH);
    } catch (error: any) {
      setErrors({
        general: error.message || 'Failed to create account. Please try again.',
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleChange = (field: keyof typeof formData) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData((prev) => ({
      ...prev,
      [field]: e.target.value,
    }));
    // Clear error for this field
    if (errors[field]) {
      setErrors((prev) => {
        const newErrors = { ...prev };
        delete newErrors[field];
        return newErrors;
      });
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <Stack spacing={3}>
        {errors.general && (
          <Alert severity="error">{errors.general}</Alert>
        )}

        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 6 }}>
            <FormControl fullWidth error={!!errors.firstName}>
              <InputLabel htmlFor="first-name-register">First Name</InputLabel>
              <OutlinedInput
                id="first-name-register"
                type="text"
                value={formData.firstName}
                onChange={handleChange('firstName')}
                label="First Name"
              />
              {errors.firstName && <FormHelperText>{errors.firstName}</FormHelperText>}
            </FormControl>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <FormControl fullWidth error={!!errors.lastName}>
              <InputLabel htmlFor="last-name-register">Last Name</InputLabel>
              <OutlinedInput
                id="last-name-register"
                type="text"
                value={formData.lastName}
                onChange={handleChange('lastName')}
                label="Last Name"
              />
              {errors.lastName && <FormHelperText>{errors.lastName}</FormHelperText>}
            </FormControl>
          </Grid>
        </Grid>

        <FormControl fullWidth error={!!errors.email}>
          <InputLabel htmlFor="email-register">Email Address</InputLabel>
          <OutlinedInput
            id="email-register"
            type="email"
            value={formData.email}
            onChange={handleChange('email')}
            label="Email Address"
          />
          {errors.email && <FormHelperText>{errors.email}</FormHelperText>}
        </FormControl>

        <FormControl fullWidth error={!!errors.password}>
          <InputLabel htmlFor="password-register">Password</InputLabel>
          <OutlinedInput
            id="password-register"
            type={showPassword ? 'text' : 'password'}
            value={formData.password}
            onChange={handleChange('password')}
            endAdornment={
              <InputAdornment position="end">
                <IconButton
                  aria-label="toggle password visibility"
                  onClick={() => setShowPassword(!showPassword)}
                  edge="end"
                  size="large"
                >
                  {showPassword ? <Visibility /> : <VisibilityOff />}
                </IconButton>
              </InputAdornment>
            }
            label="Password"
          />
          {errors.password && <FormHelperText>{errors.password}</FormHelperText>}
        </FormControl>

        <FormControl error={!!errors.terms}>
          <FormControlLabel
            control={
              <Checkbox
                checked={agreeTerms}
                onChange={(e) => {
                  setAgreeTerms(e.target.checked);
                  if (errors.terms) {
                    setErrors((prev) => {
                      const newErrors = { ...prev };
                      delete newErrors.terms;
                      return newErrors;
                    });
                  }
                }}
                name="agreeTerms"
                color="primary"
              />
            }
            label={
              <Typography variant="subtitle1">
                I agree to the{' '}
                <Typography variant="subtitle1" component="span" color="primary">
                  Terms & Conditions
                </Typography>
              </Typography>
            }
          />
          {errors.terms && <FormHelperText>{errors.terms}</FormHelperText>}
        </FormControl>

        <Box>
          <Button
            disableElevation
            disabled={isSubmitting}
            fullWidth
            size="large"
            type="submit"
            variant="contained"
            color="secondary"
          >
            {isSubmitting ? 'Creating Account...' : 'Sign Up'}
          </Button>
        </Box>

        <Box sx={{ mt: 3 }}>
          <Typography
            variant="body2"
            color="textSecondary"
            sx={{ textAlign: 'center', mb: 2 }}
          >
            Or continue with
          </Typography>
          <SocialLoginButtons
            onError={(error) => setErrors({ general: error })}
            onSuccess={() => router.push(DASHBOARD_PATH)}
          />
        </Box>
      </Stack>
    </form>
  );
};

export default AuthSupabaseRegister;
