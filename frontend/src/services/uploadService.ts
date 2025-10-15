import axios from 'axios';

export interface UploadProgress {
  fileName: string;
  progress: number;
  status: 'pending' | 'uploading' | 'complete' | 'error';
  error?: string;
}

class UploadService {
  async uploadFile(presignedUrl: string, file: File, onProgress?: (progress: number) => void): Promise<void> {
    await axios.put(presignedUrl, file, {
      headers: {
        'Content-Type': file.type
      },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total) {
          const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress?.(percentCompleted);
        }
      }
    });
  }

  async uploadFiles(
    files: File[],
    presignedUrls: string[],
    onProgress?: (fileName: string, progress: number) => void
  ): Promise<void> {
    const uploads = files.map((file, index) =>
      this.uploadFile(presignedUrls[index]!, file, (progress) => {
        onProgress?.(file.name, progress);
      })
    );

    await Promise.all(uploads);
  }
}

export const uploadService = new UploadService();
