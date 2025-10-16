'use client';

import React, { useRef } from 'react';
import { Box, Button, Typography, IconButton, LinearProgress } from '@mui/material';
import { IconUpload, IconX } from '@tabler/icons-react';
import AttachmentTwoToneIcon from '@mui/icons-material/AttachmentTwoTone';

interface FileUploadProps {
  onFileSelect: (file: File) => void;
  accept?: string;
  label?: string;
  disabled?: boolean;
  loading?: boolean;
  selectedFile?: File | null | undefined;
  onClear?: () => void;
  maxSize?: number; // in MB
}

const FileUpload: React.FC<FileUploadProps> = ({
  onFileSelect,
  accept = '*/*',
  label = 'Choose File',
  disabled = false,
  loading = false,
  selectedFile = null,
  onClear,
  maxSize = 10,
}) => {
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      // Check file size
      const fileSizeMB = file.size / (1024 * 1024);
      if (fileSizeMB > maxSize) {
        alert(`File size must be less than ${maxSize}MB`);
        return;
      }
      onFileSelect(file);
    }
  };

  const handleClear = () => {
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
    onClear?.();
  };

  return (
    <Box>
      <input
        type="file"
        ref={fileInputRef}
        style={{ display: 'none' }}
        accept={accept}
        onChange={handleFileChange}
        disabled={disabled || loading}
      />
      
      {selectedFile ? (
        <Box
          sx={{
            p: 2,
            border: 1,
            borderColor: 'primary.main',
            borderRadius: 1,
            bgcolor: 'background.paper',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <AttachmentTwoToneIcon color="primary" />
            <Box>
              <Typography variant="body2" fontWeight="bold">
                {selectedFile.name}
              </Typography>
              <Typography variant="caption" color="textSecondary">
                {(selectedFile.size / 1024).toFixed(2)} KB
              </Typography>
            </Box>
          </Box>
          <IconButton size="small" onClick={handleClear} disabled={disabled || loading}>
            <IconX size={18} />
          </IconButton>
        </Box>
      ) : (
        <Button
          variant="outlined"
          onClick={handleClick}
          disabled={disabled || loading}
          startIcon={<IconUpload size={18} />}
          fullWidth
          sx={{ py: 1.5 }}
        >
          {label}
        </Button>
      )}
      
      {loading && (
        <Box sx={{ mt: 1 }}>
          <LinearProgress />
        </Box>
      )}
    </Box>
  );
};

export default FileUpload;
