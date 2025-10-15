'use client';

import React, { useState } from 'react';
import { Box, Button, Card, CardContent, TextField, Typography, Alert, CircularProgress, Chip, Grid } from '@mui/material';
import { useAdvancedForm } from '@/hooks/enterprise/useAdvancedForm';
import { useMutation } from '@tanstack/react-query';
import { aiProfileApi } from '@/services/ai-profile-api';
import { AIProfile, AIProfileData, GenerateProfileRequest } from '@/types/ai-profile';
import MainCard from '@/components/ui-component/cards/MainCard';
import { withErrorBoundary } from '@/components/enterprise/HOCs/withErrorBoundary';

interface FormData {
  cvContent: string;
}

const AIProfileGenerate: React.FC = () => {
  const [generatedProfile, setGeneratedProfile] = useState<AIProfile | null>(null);
  const [profileData, setProfileData] = useState<AIProfileData | null>(null);

  // Mutation for generating profile
  const generateMutation = useMutation({
    mutationFn: (request: GenerateProfileRequest) => aiProfileApi.generateProfile(request),
    onSuccess: (data) => {
      setGeneratedProfile(data);
      // Parse AI attributes
      try {
        const parsed = aiProfileApi.parseAiAttributes(data.aiAttributes);
        setProfileData(parsed);
      } catch (error) {
        console.error('Error parsing profile data:', error);
      }
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
    <Box>
      <MainCard title="AI Profile Generator" secondary={
        <Typography variant="body2" color="textSecondary">
          Generate your professional profile from your CV using AI
        </Typography>
      }>
        <Box sx={{ mb: 3 }}>
          <form onSubmit={form.handleSubmit()}>
            <TextField
              label="Paste your CV content"
              placeholder="Paste your CV or resume content here..."
              value={form.values.cvContent}
              onChange={form.handleChange('cvContent')}
              onBlur={form.handleBlur('cvContent')}
              error={Boolean(form.touched.cvContent && form.errors.cvContent)}
              helperText={(form.touched.cvContent && form.errors.cvContent) || ''}
              multiline
              rows={12}
              fullWidth
              sx={{ mb: 2 }}
            />

            {generateMutation.isError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                Failed to generate profile. Please try again.
              </Alert>
            )}

            <Box sx={{ display: 'flex', gap: 2 }}>
              <Button
                type="submit"
                variant="contained"
                color="primary"
                disabled={!form.isValid || form.isSubmitting || generateMutation.isPending}
                startIcon={generateMutation.isPending ? <CircularProgress size={20} /> : null}
              >
                {generateMutation.isPending ? 'Generating...' : 'Generate Profile'}
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

        {/* Display Generated Profile */}
        {generatedProfile && profileData && (
          <Box sx={{ mt: 4 }}>
            <Typography variant="h4" gutterBottom>
              Generated Profile
            </Typography>

            <Alert severity="success" sx={{ mb: 3 }}>
              Profile generated successfully! Status: {generatedProfile.status}
            </Alert>

            <Grid container spacing={3}>
              {/* Basic Info */}
              <Grid size={{ xs: 12 }}>
                <Card>
                  <CardContent>
                    <Typography variant="h5" gutterBottom>
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
              <Grid size={{ xs: 12 }}>
                <Card>
                  <CardContent>
                    <Typography variant="h6" gutterBottom>
                      Profile Summary
                    </Typography>
                    <Typography variant="body1">
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

              {/* Companies */}
              <Grid size={{ xs: 12 }}>
                <Card>
                  <CardContent>
                    <Typography variant="h6" gutterBottom>
                      Work Experience
                    </Typography>
                    {profileData.companies.map((company, index) => (
                      <Box key={index} sx={{ mb: 2 }}>
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
                      {Object.entries(profileData.photoSuggestions).map(([key, suggestion]) => (
                        <Box key={key} sx={{ mb: 2 }}>
                          <Typography variant="subtitle2" fontWeight="bold">
                            {key.replace(/([A-Z])/g, ' $1').trim()}
                            {suggestion.required && (
                              <Chip label="Required" size="small" color="error" sx={{ ml: 1 }} />
                            )}
                          </Typography>
                          <Typography variant="body2" color="textSecondary">
                            {suggestion.description}
                          </Typography>
                          <Typography variant="caption" color="textSecondary">
                            Count: {suggestion.count}
                          </Typography>
                        </Box>
                      ))}
                    </CardContent>
                  </Card>
                </Grid>
              )}
            </Grid>
          </Box>
        )}
      </MainCard>
    </Box>
  );
};

export default withErrorBoundary(AIProfileGenerate);
