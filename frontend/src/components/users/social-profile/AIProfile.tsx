'use client';

import React, { useState } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  CardMedia,
  Chip,
  Grid,
  TextField,
  Typography,
  Alert,
  CircularProgress,
  Divider,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material';
import {
  CloudUpload as CloudUploadIcon,
  Add as AddIcon,
  Delete as DeleteIcon,
  Edit as EditIcon,
} from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { useAIProfile } from 'contexts/AIProfileContext';
import { PhotoType, AIProfileData } from 'types/ai-profile';
import MainCard from 'ui-component/cards/MainCard';
import Loader from 'ui-component/Loader';

// ==============================|| AI PROFILE COMPONENT ||============================== //

const AIProfile = () => {
  const theme = useTheme();
  const { aiProfileData, isLoading, error, generateProfile, uploadCV, uploadPhoto, clearError } = useAIProfile();
  
  const [cvContent, setCvContent] = useState('');
  const [showCvDialog, setShowCvDialog] = useState(false);
  const [uploadedFiles, setUploadedFiles] = useState<File[]>([]);

  // Handle CV content submission
  const handleGenerateProfile = async () => {
    if (!cvContent.trim()) {
      return;
    }
    await generateProfile(cvContent);
    setShowCvDialog(false);
  };

  // Handle CV file upload
  const handleCVUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      await uploadCV(file);
    }
  };

  // Handle photo upload
  const handlePhotoUpload = async (file: File, photoType: PhotoType) => {
    await uploadPhoto(file, photoType);
  };

  // Format category title
  const formatCategoryTitle = (category: string): string => {
    const titleMap = {
      profilePhoto: 'Profile Photo',
      coverPhoto: 'Cover Photo',
      professional: 'Professional Photos',
      team: 'Team Photos',
      project: 'Project Photos',
    };
    return titleMap[category as keyof typeof titleMap] || category;
  };

  // Check if photo is placeholder
  const isPlaceholder = (photoUrl: string): boolean => {
    return photoUrl.startsWith('placeholder://');
  };

  // Render photo upload section
  const renderPhotoUploadSection = () => {
    if (!aiProfileData?.photoSuggestions) {
      return null;
    }

    return (
      <Grid container spacing={3}>
        {Object.entries(aiProfileData.photoSuggestions).map(([category, suggestion]) => (
          <Grid key={category} size={{ xs: 12, md: 6 }}>
            <Card>
              <CardContent>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                  <Typography variant="h6">{formatCategoryTitle(category)}</Typography>
                  {suggestion.required && (
                    <Chip label="Required" color="error" size="small" sx={{ ml: 1 }} />
                  )}
                </Box>
                
                <Typography variant="body2" color="textSecondary" sx={{ mb: 2 }}>
                  {suggestion.description}
                </Typography>
                
                {/* AI Suggestions */}
                <Box sx={{ mb: 2 }}>
                  <Typography variant="caption" color="primary">
                    AI Suggestions:
                  </Typography>
                  <Box sx={{ mt: 0.5 }}>
                    {suggestion.suggestions.map((suggestionText, index) => (
                      <Chip
                        key={index}
                        label={suggestionText}
                        size="small"
                        variant="outlined"
                        sx={{ mr: 1, mb: 0.5 }}
                      />
                    ))}
                  </Box>
                </Box>

                {/* Photo Display/Upload */}
                <Box sx={{ mt: 2 }}>
                  {Array.from({ length: suggestion.count }, (_, index) => {
                    const photoKey = category === 'profilePhoto' || category === 'coverPhoto' 
                      ? category 
                      : `${category}[${index}]`;
                    const photoUrl = aiProfileData.photos[category as keyof typeof aiProfileData.photos];
                    const currentPhoto = Array.isArray(photoUrl) ? photoUrl[index] : photoUrl;
                    
                    return (
                      <Box key={index} sx={{ mb: 2 }}>
                        {currentPhoto && !isPlaceholder(currentPhoto) ? (
                          <Box sx={{ position: 'relative' }}>
                            <CardMedia
                              component="img"
                              image={currentPhoto}
                              alt={`${category} ${index + 1}`}
                              sx={{ height: 150, objectFit: 'cover', borderRadius: 1 }}
                            />
                            <IconButton
                              size="small"
                              sx={{ position: 'absolute', top: 8, right: 8, bgcolor: 'rgba(0,0,0,0.5)', color: 'white' }}
                            >
                              <EditIcon fontSize="small" />
                            </IconButton>
                          </Box>
                        ) : (
                          <PhotoUploadSlot
                            category={category as PhotoType}
                            suggestion={suggestion.suggestions[index] || suggestion.suggestions[0]}
                            onUpload={(file) => handlePhotoUpload(file, category as PhotoType)}
                            isRequired={suggestion.required && index === 0}
                          />
                        )}
                      </Box>
                    );
                  })}
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    );
  };

  if (isLoading) {
    return <Loader />;
  }

  return (
    <Grid container spacing={3}>
      {/* Error Display */}
      {error && (
        <Grid size={{ xs: 12 }}>
          <Alert severity="error" onClose={clearError}>
            {error}
          </Alert>
        </Grid>
      )}

      {/* CV Upload Section */}
      {!aiProfileData && (
        <Grid size={{ xs: 12 }}>
          <MainCard>
            <CardContent>
              <Typography variant="h5" gutterBottom>
                Create Your AI Profile
              </Typography>
              <Typography variant="body2" color="textSecondary" sx={{ mb: 3 }}>
                Upload your CV or paste your content to generate an AI-powered profile with suggested photos.
              </Typography>
              
              <Box sx={{ display: 'flex', gap: 2, mb: 3 }}>
                <Button
                  variant="contained"
                  startIcon={<CloudUploadIcon />}
                  component="label"
                >
                  Upload CV
                  <input
                    type="file"
                    hidden
                    accept=".pdf,.doc,.docx,.txt"
                    onChange={handleCVUpload}
                  />
                </Button>
                
                <Button
                  variant="outlined"
                  onClick={() => setShowCvDialog(true)}
                >
                  Paste Content
                </Button>
              </Box>
            </CardContent>
          </MainCard>
        </Grid>
      )}

      {/* AI Profile Data Display */}
      {aiProfileData && (
        <>
          {/* Profile Overview */}
          <Grid size={{ xs: 12 }}>
            <MainCard>
              <CardContent>
                <Typography variant="h5" gutterBottom>
                  AI-Generated Profile
                </Typography>
                <Typography variant="body2" color="textSecondary" gutterBottom>
                  This profile was generated from your CV using AI technology
                </Typography>
                
                <Grid container spacing={2} sx={{ mt: 2 }}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Typography variant="h6">Name: {aiProfileData.name}</Typography>
                    <Typography variant="subtitle1" color="textSecondary">
                      {aiProfileData.jobTitle}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Typography variant="body2">
                      <strong>Experience:</strong> {aiProfileData.experience} years
                    </Typography>
                    <Typography variant="body2">
                      <strong>Skills:</strong> {aiProfileData.skills.join(', ')}
                    </Typography>
                  </Grid>
                </Grid>
                
                <Divider sx={{ my: 2 }} />
                
                <Typography variant="h6" gutterBottom>
                  Profile Summary
                </Typography>
                <Typography variant="body2">
                  {aiProfileData.profileSummary}
                </Typography>
              </CardContent>
            </MainCard>
          </Grid>

          {/* Company Experience */}
          {aiProfileData.companies && aiProfileData.companies.length > 0 && (
            <Grid size={{ xs: 12 }}>
              <MainCard>
                <CardContent>
                  <Typography variant="h6" gutterBottom>
                    Work Experience
                  </Typography>
                  {aiProfileData.companies.map((company, index) => (
                    <Box key={index} sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                      {company.icon && (
                        <img 
                          src={company.icon} 
                          alt={company.name}
                          style={{ width: 32, height: 32, marginRight: 12, borderRadius: 4 }}
                        />
                      )}
                      <Box>
                        <Typography variant="body1" fontWeight="bold">
                          {company.position}
                        </Typography>
                        <Typography variant="body2" color="textSecondary">
                          {company.name} • {company.duration}
                        </Typography>
                      </Box>
                    </Box>
                  ))}
                </CardContent>
              </MainCard>
            </Grid>
          )}

          {/* Photo Upload Section */}
          <Grid size={{ xs: 12 }}>
            <MainCard>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Upload Your Photos
                </Typography>
                <Typography variant="body2" color="textSecondary" gutterBottom>
                  Based on your CV, we suggest these types of photos to enhance your profile:
                </Typography>
                {renderPhotoUploadSection()}
              </CardContent>
            </MainCard>
          </Grid>
        </>
      )}

      {/* CV Content Dialog */}
      <Dialog open={showCvDialog} onClose={() => setShowCvDialog(false)} maxWidth="md" fullWidth>
        <DialogTitle>Paste Your CV Content</DialogTitle>
        <DialogContent>
          <TextField
            multiline
            rows={10}
            fullWidth
            placeholder="Paste your CV content here..."
            value={cvContent}
            onChange={(e) => setCvContent(e.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowCvDialog(false)}>Cancel</Button>
          <Button 
            onClick={handleGenerateProfile} 
            variant="contained"
            disabled={!cvContent.trim()}
          >
            Generate Profile
          </Button>
        </DialogActions>
      </Dialog>
    </Grid>
  );
};

// Photo Upload Slot Component
interface PhotoUploadSlotProps {
  category: PhotoType;
  suggestion: string;
  onUpload: (file: File) => void;
  isRequired: boolean;
}

const PhotoUploadSlot: React.FC<PhotoUploadSlotProps> = ({ category, suggestion, onUpload, isRequired }) => {
  const [dragActive, setDragActive] = useState(false);

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const file = e.dataTransfer.files[0];
    if (file && file.type.startsWith('image/')) {
      onUpload(file);
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      onUpload(file);
    }
  };

  return (
    <Card
      variant="outlined"
      sx={{
        minHeight: 150,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        cursor: 'pointer',
        border: dragActive ? '2px dashed #1976d2' : '1px dashed #ccc',
        backgroundColor: dragActive ? '#f0f8ff' : 'white',
        '&:hover': {
          borderColor: '#1976d2',
          backgroundColor: '#f0f8ff',
        },
      }}
      onDrop={handleDrop}
      onDragOver={(e) => e.preventDefault()}
      onDragEnter={() => setDragActive(true)}
      onDragLeave={() => setDragActive(false)}
    >
      <CloudUploadIcon sx={{ fontSize: 48, color: 'grey.400', mb: 1 }} />
      <Typography variant="body2" color="textSecondary" textAlign="center">
        {suggestion}
      </Typography>
      <Typography variant="caption" color="textSecondary">
        {isRequired ? 'Required' : 'Optional'}
      </Typography>
      <input
        type="file"
        accept="image/*"
        onChange={handleFileSelect}
        style={{ display: 'none' }}
        id={`upload-${category}`}
      />
      <label htmlFor={`upload-${category}`}>
        <Button variant="outlined" size="small" sx={{ mt: 1 }}>
          Choose Photo
        </Button>
      </label>
    </Card>
  );
};

export default AIProfile;
