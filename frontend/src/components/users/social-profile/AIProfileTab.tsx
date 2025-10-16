'use client';

import React, { useState } from 'react';
import { Box, Button, Card, CardContent, TextField, Typography, Alert, CircularProgress, Chip, Grid, Divider, Tab, Tabs } from '@mui/material';
import { IconCheck, IconUpload } from '@tabler/icons-react';
import { useAdvancedForm } from '@/hooks/enterprise/useAdvancedForm';
import { useMutation } from '@tanstack/react-query';
import { aiProfileApi } from '@/services/ai-profile-api';
import { AIProfile, AIProfileData, GenerateProfileRequest } from '@/types/ai-profile';
import MainCard from '@/components/ui-component/cards/MainCard';
import FileUpload from '@/components/ui-component/FileUpload';
import { withErrorBoundary } from '@/components/enterprise/HOCs/withErrorBoundary';
import { useNotifications } from '@/contexts/NotificationContext';

interface FormData {
  cvContent: string;
}

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div role="tabpanel" hidden={value !== index} {...other}>
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
}

const AIProfileTab: React.FC = () => {
  const [generatedProfile, setGeneratedProfile] = useState<AIProfile | null>(null);
  const [profileData, setProfileData] = useState<AIProfileData | null>(null);
  const [inputMethod, setInputMethod] = useState(0); // 0 = text, 1 = file
  const [cvFile, setCvFile] = useState<File | null>(null);
  const [photoFiles, setPhotoFiles] = useState<{ [key: string]: File | null }>({});
  const { showNotification } = useNotifications();

  // Error handler function
  const handleError = () => {
    showNotification({
      open: true,
      message: 'Failed to process request. Please try again.',
      variant: 'alert',
      alert: { color: 'error', variant: 'filled' },
      close: true,
    });
  };

  // Success handler for profile generation
  const handleProfileGenerated = (data: AIProfile) => {
    setGeneratedProfile(data);
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
  };

  // Mutation for generating profile from text
  const generateMutation = useMutation({
    mutationFn: (request: GenerateProfileRequest) => aiProfileApi.generateProfile(request),
    onSuccess: (data) => handleProfileGenerated(data),
    onError: handleError,
  });

  // Mutation for uploading CV file
  const uploadCVMutation = useMutation({
    mutationFn: (file: File) => aiProfileApi.uploadCVFile(file),
    onSuccess: (data) => handleProfileGenerated(data),
    onError: handleError,
  });

  // Mutation for uploading photos
  const uploadPhotoMutation = useMutation({
    mutationFn: ({ profileId, file, photoType }: { profileId: string; file: File; photoType: string }) =>
      aiProfileApi.uploadPhoto(profileId, file, photoType),
    onSuccess: (data) => {
      const parsed = aiProfileApi.parseAiAttributes(data.aiAttributes);
      setProfileData(parsed);
      setGeneratedProfile(data);
      showNotification({
        open: true,
        message: 'Photo uploaded successfully!',
        variant: 'alert',
        alert: { color: 'success', variant: 'filled' },
        close: true,
      });
    },
    onError: handleError,
  });

  // Mutation for publishing profile
  const publishMutation = useMutation({
    mutationFn: (profileId: string) => aiProfileApi.publishProfile(profileId),
    onSuccess: () => {
      showNotification({
        open: true,
        message: 'Profile published successfully!',
        variant: 'alert',
        alert: { color: 'success', variant: 'filled' },
        close: true,
      });
    },
    onError: handleError,
  });

  // Form setup for text input
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

  const handleFileUpload = () => {
    if (cvFile) {
      uploadCVMutation.mutate(cvFile);
    }
  };

  const handlePhotoUpload = (photoType: string) => {
    const file = photoFiles[photoType];
    if (file && generatedProfile) {
      uploadPhotoMutation.mutate({ profileId: generatedProfile.id, file, photoType });
      // Clear the file after upload
      setPhotoFiles(prev => ({ ...prev, [photoType]: null }));
    }
  };

  const handlePublish = () => {
    if (generatedProfile) {
      publishMutation.mutate(generatedProfile.id);
    }
  };

  const isPublished = generatedProfile?.status === 'COMPLETE';

  return (
    <Grid container spacing={3}>
      <Grid size={{ xs: 12 }}>
        <MainCard
          title="Generate Profile with AI"
          secondary={
            <Typography variant="body2" color="textSecondary">
              Upload your CV or paste the content to generate a professional profile
            </Typography>
          }
        >
          <Tabs value={inputMethod} onChange={(_, v) => setInputMethod(v)} sx={{ mb: 3 }}>
            <Tab label="Paste CV Text" />
            <Tab label="Upload CV File" />
          </Tabs>

          <TabPanel value={inputMethod} index={0}>
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
                  startIcon={generateMutation.isPending ? <CircularProgress size={20} /> : <IconUpload size={18} />}
                >
                  {generateMutation.isPending ? 'Generating...' : 'Generate Profile'}
                </Button>

                {form.isDirty && (
                  <Button variant="outlined" onClick={form.resetForm} disabled={form.isSubmitting}>
                    Reset
                  </Button>
                )}
              </Box>
            </form>
          </TabPanel>

          <TabPanel value={inputMethod} index={1}>
            <FileUpload
              onFileSelect={setCvFile}
              accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
              label="Upload CV (PDF or Word)"
              selectedFile={cvFile}
              onClear={() => setCvFile(null)}
              loading={uploadCVMutation.isPending}
              maxSize={10}
            />

            <Box sx={{ mt: 2 }}>
              <Button
                variant="contained"
                color="primary"
                onClick={handleFileUpload}
                disabled={!cvFile || uploadCVMutation.isPending}
                startIcon={uploadCVMutation.isPending ? <CircularProgress size={20} /> : <IconUpload size={18} />}
              >
                {uploadCVMutation.isPending ? 'Processing...' : 'Generate Profile from File'}
              </Button>
            </Box>
          </TabPanel>
        </MainCard>
      </Grid>

      {/* Display Generated Profile */}
      {generatedProfile && profileData && (
        <>
          <Grid size={{ xs: 12 }}>
            <Alert severity="success" action={
              !isPublished && (
                <Button
                  color="inherit"
                  size="small"
                  onClick={handlePublish}
                  disabled={publishMutation.isPending}
                  startIcon={publishMutation.isPending ? <CircularProgress size={16} /> : <IconCheck size={16} />}
                >
                  {publishMutation.isPending ? 'Publishing...' : 'Publish Profile'}
                </Button>
              )
            }>
              {isPublished ? 'Profile published! You can now use this information in your social profile.' : 'Profile generated successfully! Review and upload photos, then publish.'}
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
                <Typography variant="body2">{profileData.profileSummary}</Typography>
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
                  <Box
                    key={index}
                    sx={{
                      mb: 2,
                      pb: 2,
                      borderBottom: index < profileData.companies.length - 1 ? 1 : 0,
                      borderColor: 'divider',
                    }}
                  >
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

          {/* Photo Upload Section */}
          {profileData.photoSuggestions && (
            <Grid size={{ xs: 12 }}>
              <Card>
                <CardContent>
                  <Typography variant="h6" gutterBottom>
                    Upload Photos
                  </Typography>
                  <Typography variant="body2" color="textSecondary" sx={{ mb: 3 }}>
                    Upload photos based on the AI suggestions below. These will be saved to your profile.
                  </Typography>

                  <Divider sx={{ mb: 3 }} />

                  <Grid container spacing={3}>
                    {Object.entries(profileData.photoSuggestions).map(([key, suggestion]) => (
                      <Grid key={key} size={{ xs: 12, md: 6 }}>
                        <Box sx={{ p: 2, bgcolor: 'background.default', borderRadius: 1 }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                            <Typography variant="subtitle2" fontWeight="bold" sx={{ textTransform: 'capitalize' }}>
                              {key.replace(/([A-Z])/g, ' $1').trim()}
                            </Typography>
                            {suggestion.required && <Chip label="Required" size="small" color="error" sx={{ ml: 1 }} />}
                          </Box>
                          <Typography variant="body2" color="textSecondary" sx={{ mb: 1 }}>
                            {suggestion.description}
                          </Typography>
                          <Typography variant="caption" color="textSecondary" display="block" sx={{ mb: 2 }}>
                            Recommended: {suggestion.count} photo{suggestion.count > 1 ? 's' : ''}
                          </Typography>

                          <FileUpload
                            onFileSelect={(file) => setPhotoFiles(prev => ({ ...prev, [key]: file }))}
                            accept="image/*"
                            label={`Upload ${key.replace(/([A-Z])/g, ' $1').trim()}`}
                            selectedFile={photoFiles[key]}
                            onClear={() => setPhotoFiles(prev => ({ ...prev, [key]: null }))}
                            loading={uploadPhotoMutation.isPending}
                            maxSize={5}
                          />

                          {photoFiles[key] && (
                            <Button
                              variant="contained"
                              size="small"
                              onClick={() => handlePhotoUpload(key)}
                              disabled={uploadPhotoMutation.isPending}
                              sx={{ mt: 1 }}
                              fullWidth
                            >
                              Upload Photo
                            </Button>
                          )}
                        </Box>
                      </Grid>
                    ))}
                  </Grid>
                </CardContent>
              </Card>
            </Grid>
          )}

          <Grid size={{ xs: 12 }}>
            <Alert severity="info">
              After uploading your photos, click "Publish Profile" to save everything. Then navigate to other tabs to complete your social profile.
            </Alert>
          </Grid>

          {/* Prominent Publish Button Section */}
          {!isPublished && (
            <Grid size={{ xs: 12 }}>
              <Card sx={{ bgcolor: 'primary.lighter', borderColor: 'primary.main', borderWidth: 2, borderStyle: 'solid' }}>
                <CardContent>
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2 }}>
                    <Box>
                      <Typography variant="h5" gutterBottom>
                        Ready to Publish?
                      </Typography>
                      <Typography variant="body2" color="textSecondary">
                        Once you publish, your AI-generated profile will be saved and marked as complete.
                      </Typography>
                    </Box>
                    <Button
                      variant="contained"
                      color="primary"
                      size="large"
                      onClick={handlePublish}
                      disabled={publishMutation.isPending}
                      startIcon={publishMutation.isPending ? <CircularProgress size={20} /> : <IconCheck size={20} />}
                      sx={{ minWidth: 180, py: 1.5 }}
                    >
                      {publishMutation.isPending ? 'Publishing...' : 'Publish Profile'}
                    </Button>
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          )}
        </>
      )}
    </Grid>
  );
};

export default withErrorBoundary(AIProfileTab);
