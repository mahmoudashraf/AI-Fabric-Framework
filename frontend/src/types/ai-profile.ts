// AI Profile Types

export type AIProfileStatus = 'DRAFT' | 'PHOTOS_PENDING' | 'COMPLETE' | 'ARCHIVED';

export interface Company {
  name: string;
  icon: string;
  position: string;
  duration: string;
}

export interface PhotoSuggestion {
  required: boolean;
  count: number;
  suggestions: string[];
  description: string;
}

export interface PhotoCollection {
  profilePhoto?: string;
  coverPhoto?: string;
  professional?: string[];
  team?: string[];
  project?: string[];
}

export interface PhotoSuggestions {
  profilePhoto?: PhotoSuggestion;
  coverPhoto?: PhotoSuggestion;
  professional?: PhotoSuggestion;
  team?: PhotoSuggestion;
  project?: PhotoSuggestion;
}

export interface AIProfileData {
  name: string;
  jobTitle: string;
  companies: Company[];
  profileSummary: string;
  skills: string[];
  experience: number;
  photos?: PhotoCollection;
  photoSuggestions?: PhotoSuggestions;
}

export interface AIProfile {
  id: string;
  userId: string;
  aiAttributes: string;
  cvFileUrl?: string;
  status: AIProfileStatus;
  createdAt: string;
  updatedAt: string;
}

export interface GenerateProfileRequest {
  cvContent: string;
}

export interface AIProfileResponse {
  id: string;
  userId: string;
  aiAttributes: string;
  cvFileUrl?: string;
  status: AIProfileStatus;
  createdAt: string;
  updatedAt: string;
}
