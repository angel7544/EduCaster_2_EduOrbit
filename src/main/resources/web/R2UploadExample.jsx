import React, { useState, useRef } from 'react';
import axios from 'axios';

// Example React Component for R2 Upload with Progress and Cancel

const R2Upload = () => {
  const [files, setFiles] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [timeLeft, setTimeLeft] = useState(null);
  const abortControllerRef = useRef(null);

  const handleDrop = (e) => {
    e.preventDefault();
    const droppedFiles = Array.from(e.dataTransfer.files);
    setFiles(droppedFiles);
  };

  const handleDragOver = (e) => {
    e.preventDefault();
  };

  const uploadFile = async () => {
    if (files.length === 0) return;

    setUploading(true);
    setProgress(0);
    setTimeLeft(null);

    const file = files[0]; // Example for single file upload
    abortControllerRef.current = new AbortController();

    try {
      // Step 1: Get Pre-signed URL from Backend
      const response = await axios.post('/api/r2/presigned-url', {
        fileName: file.name,
        contentType: file.type
      });

      const { uploadUrl, key, publicUrl } = response.data;

      // Step 2: Upload directly to R2 using Pre-signed URL
      const startTime = Date.now();

      await axios.put(uploadUrl, file, {
        headers: {
          'Content-Type': file.type
        },
        signal: abortControllerRef.current.signal,
        onUploadProgress: (progressEvent) => {
          const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          setProgress(percentCompleted);

          // Calculate time remaining
          const timeElapsed = (Date.now() - startTime) / 1000; // seconds
          const uploadSpeed = progressEvent.loaded / timeElapsed; // bytes/second
          const remainingBytes = progressEvent.total - progressEvent.loaded;
          const secondsLeft = remainingBytes / uploadSpeed;
          
          if (isFinite(secondsLeft)) {
            setTimeLeft(Math.round(secondsLeft));
          }
        }
      });

      alert(`Upload successful!\nFile Key: ${key}\nPublic URL: ${publicUrl}`);
      console.log('Public URL:', publicUrl);
    } catch (error) {
      if (axios.isCancel(error)) {
        alert('Upload cancelled');
      } else {
        console.error('Upload failed:', error);
        alert('Upload failed');
      }
    } finally {
      setUploading(false);
      abortControllerRef.current = null;
    }
  };

  const cancelUpload = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
  };

  return (
    <div style={{ padding: '20px', maxWidth: '500px', margin: '0 auto' }}>
      <h2>Upload to Cloudflare R2</h2>
      
      <div 
        onDrop={handleDrop} 
        onDragOver={handleDragOver} 
        style={{
          border: '2px dashed #ccc', 
          padding: '40px', 
          textAlign: 'center',
          marginBottom: '20px',
          cursor: 'pointer'
        }}
      >
        {files.length > 0 ? (
          <p>{files[0].name} ({(files[0].size / 1024 / 1024).toFixed(2)} MB)</p>
        ) : (
          <p>Drag & Drop files here or click to select</p>
        )}
        <input 
          type="file" 
          onChange={(e) => setFiles(Array.from(e.target.files))} 
          style={{ display: 'none' }} 
        />
      </div>

      {uploading && (
        <div style={{ marginBottom: '20px' }}>
          <div style={{ height: '20px', background: '#eee', borderRadius: '4px', overflow: 'hidden' }}>
            <div 
              style={{ 
                width: `${progress}%`, 
                height: '100%', 
                background: '#4CAF50', 
                transition: 'width 0.2s' 
              }} 
            />
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '5px' }}>
            <span>{progress}%</span>
            {timeLeft !== null && <span>~{timeLeft}s remaining</span>}
          </div>
        </div>
      )}

      <div style={{ display: 'flex', gap: '10px' }}>
        <button 
          onClick={uploadFile} 
          disabled={uploading || files.length === 0}
          style={{ padding: '10px 20px', background: '#007bff', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
        >
          {uploading ? 'Uploading...' : 'Upload'}
        </button>
        
        {uploading && (
          <button 
            onClick={cancelUpload}
            style={{ padding: '10px 20px', background: '#dc3545', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
          >
            Cancel
          </button>
        )}
      </div>
    </div>
  );
};

export default R2Upload;
