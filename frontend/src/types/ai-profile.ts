// AI Profile Types
export interface AIProfileData {
  name: string;
  jobTitle: string;
  companies: Company[];
  profileSummary: string;
  skills: string[];
  experience: number;
  photos: PhotoCollection;
  photoSuggestions: PhotoSuggestions;
}

export interface Company {
  name: string;
  icon: string;
  position: string;
  duration: string;
}

export interface PhotoCollection {
  profilePhoto: string;
  coverPhoto: string;
  professional: string[];
  team: string[];
  project: string[];
}

export interface PhotoSuggestions {
  profilePhoto: PhotoSuggestion;
  professional: PhotoSuggestion;
  team: PhotoSuggestion;
  project: PhotoSuggestion;
}

export interface PhotoSuggestion {
  required: boolean;
  count: number;
  suggestions: string[];
  description: string;
}

export interface AIProfile {
  id: string;
  userId: string;
  aiAttributes: string; // JSON string
  cvFileUrl?: string;
  status: AIProfileStatus;
  createdAt: string;
  updatedAt: string;
}

export enum AIProfileStatus {
  DRAFT = 'DRAFT',
  PHOTOS_PENDING = 'PHOTOS_PENDING',
  COMPLETE = 'COMPLETE',
  ARCHIVED = 'ARCHIVED'
}

export interface PhotoUpload {
  id: string;
  type: PhotoType;
  file: File;
  preview: string;
  aiSuggested: boolean;
  description: string;
}

export type PhotoType = 'profilePhoto' | 'coverPhoto' | 'professional' | 'team' | 'project' | 'custom';

export interface AIProfileContextType {
  aiProfile: AIProfile | null;
  aiProfileData: AIProfileData | null;
  isLoading: boolean;
  error: string | null;
  generateProfile: (cvContent: string) => Promise<void>;
  uploadCV: (file: File) => Promise<void>;
  uploadPhoto: (file: File, photoType: PhotoType) => Promise<void>;
  updateProfile: (profileData: Partial<AIProfileData>) => Promise<void>;
  enrichWithPhotos: (photoUrls: Record<string, string>) => Promise<void>;
  clearError: () => void;
}
