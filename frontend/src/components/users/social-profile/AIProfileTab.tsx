'use client';

import React, { useState } from 'react';
import { Box, Button, Card, CardContent, TextField, Typography, Alert, CircularProgress, Chip, Grid } from '@mui/material';
import { useAdvancedForm } from '@/hooks/enterprise/useAdvancedForm';
import { useMutation } from '@tanstack/react-query';
import { aiProfileApi } from '@/services/ai-profile-api';
import { AIProfile, AIProfileData, GenerateProfileRequest } from '@/types/ai-profile';
import MainCard from '@/components/ui-component/cards/MainCard';
import { withErrorBoundary } from '@/components/enterprise/HOCs/withErrorBoundary';
import { useNotifications } from '@/contexts/NotificationContext';

interface FormData {
  cvContent: string;
}

const AIProfileTab: React.FC = () => {
  const [generatedProfile, setGeneratedProfile] = useState<AIProfile | null>(null);
  const [profileData, setProfileData] = useState<AIProfileData | null>(null);
  const { showNotification } = useNotifications();

  // Mutation for generating profile
  const generateMutation = useMutation({
    mutationFn: (request: GenerateProfileRequest) => aiProfileApi.generateProfile(request),
    onSuccess: (data) => {
      setGeneratedProfile(data);
      // Parse AI attributes
      try {
        const parsed = aiProfileApi.parseAiAttributes(data.aiAttributes);
        setProfileData(parsed);
        showNotification({
          open: true,
          message: 'Profile generated successfully!',
          variant: 'alert',
          alert: { color: 'success', variant: 'filled' },
          close: true,
        });
      } catch (error) {
        console.error('Error parsing profile data:', error);
        showNotification({
          open: true,
          message: 'Error parsing AI profile data',
          variant: 'alert',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      }
    },
    onError: () => {
      showNotification({
        open: true,
        message: 'Failed to generate profile. Please try again.',
        variant: 'alert',
        alert: { color: 'error', variant: 'filled' },
        close: true,
      });
    },
  });

  // Form setup
  const form = useAdvancedForm<FormData>({
    initialValues: {
      cvContent: '',
    },
    validationRules: {
      cvContent: [
        { type: 'required', message: 'CV content is required' },
        { type: 'minLength', value: 100, message: 'CV content must be at least 100 characters' },
        { type: 'maxLength', value: 50000, message: 'CV content must not exceed 50000 characters' },
      ],
    },
    onSubmit: async (values) => {
      await generateMutation.mutateAsync({ cvContent: values.cvContent });
    },
  });

  return (
    <Grid container spacing={3}>
      <Grid size={{ xs: 12 }}>
        <MainCard title="Generate Profile with AI" secondary={
          <Typography variant="body2" color="textSecondary">
            Paste your CV content below and let AI generate a professional profile for you
          </Typography>
        }>
          <Box sx={{ mb: 3 }}>
            <form onSubmit={form.handleSubmit()}>
              <TextField
                label="Paste your CV content"
                placeholder="Paste your CV or resume content here..."
                value={form.values.cvContent}
                onChange={(e) => form.handleChange('cvContent')(e.target.value)}
                onBlur={form.handleBlur('cvContent')}
                error={Boolean(form.touched.cvContent && form.errors.cvContent)}
                helperText={(form.touched.cvContent && form.errors.cvContent) || ''}
                multiline
                rows={12}
                fullWidth
                sx={{ mb: 2 }}
              />

              <Box sx={{ display: 'flex', gap: 2 }}>
                <Button
                  type="submit"
                  variant="contained"
                  color="primary"
                  disabled={!form.isValid || form.isSubmitting || generateMutation.isPending}
                  startIcon={generateMutation.isPending ? <CircularProgress size={20} /> : null}
                >
                  {generateMutation.isPending ? 'Generating...' : 'Generate Profile with AI'}
                </Button>

                {form.isDirty && (
                  <Button
                    variant="outlined"
                    onClick={form.resetForm}
                    disabled={form.isSubmitting}
                  >
                    Reset
                  </Button>
                )}
              </Box>
            </form>
          </Box>
        </MainCard>
      </Grid>

      {/* Display Generated Profile */}
      {generatedProfile && profileData && (
        <>
          <Grid size={{ xs: 12 }}>
            <Alert severity="success">
              Profile generated successfully! Review the information below and use it to update your social profile.
            </Alert>
          </Grid>

          {/* Basic Info */}
          <Grid size={{ xs: 12, md: 6 }}>
            <Card>
              <CardContent>
                <Typography variant="h5" gutterBottom color="primary">
                  {profileData.name}
                </Typography>
                <Typography variant="subtitle1" color="textSecondary" gutterBottom>
                  {profileData.jobTitle}
                </Typography>
                <Typography variant="body2" color="textSecondary">
                  Experience: {profileData.experience} years
                </Typography>
              </CardContent>
            </Card>
          </Grid>

          {/* Profile Summary */}
          <Grid size={{ xs: 12, md: 6 }}>
            <Card sx={{ height: '100%' }}>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Profile Summary
                </Typography>
                <Typography variant="body2">
                  {profileData.profileSummary}
                </Typography>
              </CardContent>
            </Card>
          </Grid>

          {/* Skills */}
          <Grid size={{ xs: 12 }}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Skills
                </Typography>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                  {profileData.skills.map((skill, index) => (
                    <Chip key={index} label={skill} color="primary" variant="outlined" />
                  ))}
                </Box>
              </CardContent>
            </Card>
          </Grid>

          {/* Work Experience */}
          <Grid size={{ xs: 12 }}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Work Experience
                </Typography>
                {profileData.companies.map((company, index) => (
                  <Box key={index} sx={{ mb: 2, pb: 2, borderBottom: index < profileData.companies.length - 1 ? 1 : 0, borderColor: 'divider' }}>
                    <Typography variant="subtitle1" fontWeight="bold">
                      {company.position}
                    </Typography>
                    <Typography variant="body2" color="textSecondary">
                      {company.name} • {company.duration}
                    </Typography>
                  </Box>
                ))}
              </CardContent>
            </Card>
          </Grid>

          {/* Photo Suggestions */}
          {profileData.photoSuggestions && (
            <Grid size={{ xs: 12 }}>
              <Card>
                <CardContent>
                  <Typography variant="h6" gutterBottom>
                    Photo Suggestions
                  </Typography>
                  <Typography variant="body2" color="textSecondary" sx={{ mb: 2 }}>
                    Based on your profile, here are some photo recommendations:
                  </Typography>
                  {Object.entries(profileData.photoSuggestions).map(([key, suggestion]) => (
                    <Box key={key} sx={{ mb: 2, p: 2, bgcolor: 'background.default', borderRadius: 1 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                        <Typography variant="subtitle2" fontWeight="bold" sx={{ textTransform: 'capitalize' }}>
                          {key.replace(/([A-Z])/g, ' $1').trim()}
                        </Typography>
                        {suggestion.required && (
                          <Chip label="Required" size="small" color="error" sx={{ ml: 1 }} />
                        )}
                      </Box>
                      <Typography variant="body2" color="textSecondary" sx={{ mb: 0.5 }}>
                        {suggestion.description}
                      </Typography>
                      <Typography variant="caption" color="textSecondary">
                        Recommended: {suggestion.count} photo{suggestion.count > 1 ? 's' : ''}
                      </Typography>
                      {suggestion.suggestions && suggestion.suggestions.length > 0 && (
                        <Box sx={{ mt: 1 }}>
                          {suggestion.suggestions.map((sug: string, idx: number) => (
                            <Chip key={idx} label={sug} size="small" sx={{ mr: 0.5, mt: 0.5 }} />
                          ))}
                        </Box>
                      )}
                    </Box>
                  ))}
                </CardContent>
              </Card>
            </Grid>
          )}

          <Grid size={{ xs: 12 }}>
            <Alert severity="info">
              Use this AI-generated information to update your profile. Navigate to other tabs to add your photo, update your bio, and connect with friends.
            </Alert>
          </Grid>
        </>
      )}
    </Grid>
  );
};

export default withErrorBoundary(AIProfileTab);
