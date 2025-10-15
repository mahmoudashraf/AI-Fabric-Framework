'use client';

import { useState, useCallback } from 'react';

// For now, we'll use a simple textarea to avoid ReactQuill compatibility issues
// TODO: Replace with a React 18 compatible rich text editor

// ==============================|| QUILL EDITOR ||============================== //

const ReactQuillDemo = () => {
  const [text, setText] = useState(
    "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum."
  );

  const handleChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setText(e.target.value);
  }, []);

  return (
    <div style={{ 
      minHeight: '200px',
      border: '1px solid #ccc',
      borderRadius: '4px',
      backgroundColor: '#f9f9f9'
    }}>
      <div style={{ 
        padding: '10px', 
        borderBottom: '1px solid #ccc', 
        backgroundColor: '#e9e9e9',
        fontSize: '14px',
        fontWeight: 'bold'
      }}>
        Text Editor (ReactQuill temporarily disabled due to React 18 compatibility)
      </div>
      <textarea
        value={text}
        onChange={handleChange}
        style={{ 
          width: '100%', 
          minHeight: '150px', 
          padding: '10px',
          border: 'none',
          outline: 'none',
          resize: 'vertical',
          fontFamily: 'inherit',
          fontSize: '14px',
          lineHeight: '1.5'
        }}
        placeholder="Enter your text here..."
      />
    </div>
  );
};

export default ReactQuillDemo;
