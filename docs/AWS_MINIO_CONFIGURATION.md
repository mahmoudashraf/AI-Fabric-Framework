# 🪣 AWS S3 / MinIO Configuration Guide

## 📋 Overview

The EasyLuxury platform uses S3-compatible storage for property media uploads. The configuration supports **both**:
- **MinIO** for local development (default)
- **AWS S3** for staging/production

---

## 🔧 Configuration Requirements

### 1️⃣ Required Environment Variables

#### Backend (`application.yml`)
```yaml
aws:
  s3:
    endpoint: ${AWS_S3_ENDPOINT:http://localhost:9000}
    region: ${AWS_S3_REGION:us-east-1}
    bucket: ${AWS_S3_BUCKET:easyluxury}
    access-key: ${AWS_ACCESS_KEY:minioadmin}
    secret-key: ${AWS_SECRET_KEY:minioadmin}
    presigned-url-duration: 900 # 15 minutes
```

#### Environment Variables to Set

| Variable | Description | Dev Default | Production Example |
|----------|-------------|-------------|-------------------|
| `AWS_S3_ENDPOINT` | S3 endpoint URL | `http://localhost:9000` | `https://s3.amazonaws.com` or leave unset |
| `AWS_S3_REGION` | AWS region | `us-east-1` | `eu-west-1`, `ap-south-1`, etc. |
| `AWS_S3_BUCKET` | Bucket name | `easyluxury` | `easyluxury-prod` |
| `AWS_ACCESS_KEY` | Access key ID | `minioadmin` | Your AWS access key |
| `AWS_SECRET_KEY` | Secret access key | `minioadmin` | Your AWS secret key |

---

## 🐳 MinIO Setup (Development)

### Option 1: Docker Compose (Recommended)

**Already configured in `docker-compose.yml`:**

```yaml
minio:
  image: minio/minio:latest
  container_name: easyluxury-minio
  command: server /data --console-address ":9001"
  environment:
    MINIO_ROOT_USER: minioadmin
    MINIO_ROOT_PASSWORD: minioadmin
  ports:
    - "9000:9000"  # API
    - "9001:9001"  # Console
  volumes:
    - minio_data:/data
```

**Start MinIO:**
```bash
docker compose up -d minio
```

**Access MinIO Console:**
- URL: http://localhost:9001
- Username: `minioadmin`
- Password: `minioadmin`

### Option 2: Standalone MinIO

```bash
# Download MinIO
wget https://dl.min.io/server/minio/release/linux-amd64/minio
chmod +x minio

# Start MinIO
MINIO_ROOT_USER=minioadmin MINIO_ROOT_PASSWORD=minioadmin \
./minio server /data --console-address ":9001"
```

---

## 🪣 Create Bucket (One-Time Setup)

### Via MinIO Console:
1. Open http://localhost:9001
2. Login with `minioadmin` / `minioadmin`
3. Click **"Buckets"** → **"Create Bucket"**
4. Name: `easyluxury`
5. Click **"Create"**

### Via MinIO Client (mc):
```bash
# Install mc
wget https://dl.min.io/client/mc/release/linux-amd64/mc
chmod +x mc

# Configure alias
mc alias set local http://localhost:9000 minioadmin minioadmin

# Create bucket
mc mb local/easyluxury

# Set public read policy (optional, for development)
mc anonymous set download local/easyluxury
```

### Via AWS CLI:
```bash
# For MinIO
aws --endpoint-url http://localhost:9000 \
    s3 mb s3://easyluxury

# For AWS S3 (production)
aws s3 mb s3://easyluxury-prod --region us-east-1
```

---

## ☁️ AWS S3 Setup (Production)

### 1. Create S3 Bucket

```bash
aws s3 mb s3://easyluxury-prod --region us-east-1
```

Or via AWS Console:
1. Go to AWS S3 Console
2. Click **"Create bucket"**
3. Name: `easyluxury-prod`
4. Region: Select your region
5. **Uncheck** "Block all public access" (for presigned URLs)
6. Enable versioning (recommended)
7. Enable encryption (recommended)

### 2. Configure CORS (Required for Direct Uploads)

```json
[
  {
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["GET", "PUT", "POST", "DELETE"],
    "AllowedOrigins": [
      "http://localhost:3000",
      "https://yourdomain.com"
    ],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3000
  }
]
```

**Apply CORS via CLI:**
```bash
aws s3api put-bucket-cors \
  --bucket easyluxury-prod \
  --cors-configuration file://cors-config.json
```

### 3. Create IAM User & Permissions

**IAM Policy (S3 Access):**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::easyluxury-prod",
        "arn:aws:s3:::easyluxury-prod/*"
      ]
    }
  ]
}
```

**Create IAM User:**
```bash
# Create user
aws iam create-user --user-name easyluxury-s3-user

# Attach policy
aws iam put-user-policy \
  --user-name easyluxury-s3-user \
  --policy-name EasyLuxuryS3Access \
  --policy-document file://s3-policy.json

# Create access keys
aws iam create-access-key --user-name easyluxury-s3-user
```

**Save the credentials:**
- Access Key ID: `AKIAIOSFODNN7EXAMPLE`
- Secret Access Key: `wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY`

### 4. Set Environment Variables (Production)

```bash
# Set in your deployment environment (.env or system vars)
export AWS_S3_REGION=us-east-1
export AWS_S3_BUCKET=easyluxury-prod
export AWS_ACCESS_KEY=AKIAIOSFODNN7EXAMPLE
export AWS_SECRET_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY

# DON'T set AWS_S3_ENDPOINT for real AWS S3 (uses default)
```

---

## 🔐 Security Best Practices

### For Development (MinIO):
✅ Use default credentials (`minioadmin`)  
✅ Endpoint: `http://localhost:9000`  
✅ No public access needed  

### For Production (AWS S3):
✅ Use IAM user with minimal permissions  
✅ **Never** commit credentials to Git  
✅ Use environment variables or AWS Secrets Manager  
✅ Enable bucket versioning  
✅ Enable server-side encryption (AES-256 or KMS)  
✅ Set up lifecycle policies for old files  
✅ Monitor with CloudWatch  
✅ Enable CloudTrail logging  

---

## 🚀 Quick Start Checklist

### Development Setup (MinIO):
- [x] Start MinIO: `docker compose up -d minio`
- [x] Access console: http://localhost:9001 (minioadmin/minioadmin)
- [ ] **Create bucket**: `easyluxury`
- [ ] Verify backend connects: Check logs on startup
- [ ] Test upload: Submit a property with photos

### Production Setup (AWS S3):
- [ ] Create S3 bucket in AWS Console
- [ ] Configure CORS policy
- [ ] Create IAM user with S3 permissions
- [ ] Generate access keys
- [ ] Set environment variables on server
- [ ] Update `application.yml` or deployment config
- [ ] Test presigned URL generation
- [ ] Test file upload

---

## 🧪 Testing Configuration

### 1. Check Backend Connection

**Run backend and look for:**
```
INFO  c.e.config.S3Config - S3 Client configured
INFO  c.e.config.S3Config - Endpoint: http://localhost:9000
INFO  c.e.config.S3Config - Region: us-east-1
INFO  c.e.config.S3Config - Bucket: easyluxury
```

### 2. Test Presigned URL Generation

**API Test:**
```bash
# Get auth token first (replace with actual JWT)
TOKEN="your-jwt-token"

# Request presigned URLs
curl -X POST http://localhost:8080/api/properties/test-presigned \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "propertyId": "123e4567-e89b-12d3-a456-426614174000",
    "fileNames": ["image1.jpg", "image2.jpg"],
    "mediaType": "PHOTO"
  }'
```

**Expected Response:**
```json
{
  "urls": [
    {
      "fileName": "image1.jpg",
      "presignedUrl": "http://localhost:9000/easyluxury/...",
      "expiresAt": "2025-10-11T12:30:00Z"
    }
  ]
}
```

### 3. Test Direct Upload

```bash
# Upload to presigned URL
curl -X PUT "http://localhost:9000/easyluxury/..." \
  -H "Content-Type: image/jpeg" \
  --data-binary @image1.jpg
```

---

## ❌ Common Issues & Solutions

### Issue: "NoSuchBucket: The specified bucket does not exist"
**Solution:**
```bash
# Create bucket in MinIO console OR
mc mb local/easyluxury
```

### Issue: "SignatureDoesNotMatch"
**Solution:**
- Check access key and secret key are correct
- For MinIO, ensure credentials match `MINIO_ROOT_USER` and `MINIO_ROOT_PASSWORD`
- For AWS, regenerate IAM access keys

### Issue: "Connection refused" to MinIO
**Solution:**
```bash
# Check MinIO is running
docker ps | grep minio

# Check logs
docker logs easyluxury-minio

# Restart MinIO
docker compose restart minio
```

### Issue: CORS errors in browser
**Solution:**
- For MinIO: Set bucket policy to allow cross-origin
- For AWS S3: Add CORS configuration (see above)

### Issue: Presigned URLs expire too quickly
**Solution:**
- Adjust `presigned-url-duration` in `application.yml` (default: 900s = 15min)
- Maximum: 7 days (604800 seconds)

---

## 📁 File Structure & Storage

### MinIO/S3 Storage Layout:
```
easyluxury/
├── properties/
│   ├── {propertyId}/
│   │   ├── photos/
│   │   │   ├── {uuid}_original.jpg
│   │   │   ├── {uuid}_thumbnail.jpg
│   │   ├── documents/
│   │   │   ├── {uuid}_deed.pdf
│   │   │   ├── {uuid}_contract.pdf
│   ├── {propertyId}/
│   │   └── ...
└── styles/
    ├── {styleId}/
    │   ├── {uuid}_image1.jpg
    │   ├── {uuid}_image2.jpg
```

---

## 🔄 Migration Between MinIO & AWS S3

### From MinIO to AWS S3:
```bash
# Use rclone or aws s3 sync
aws s3 sync s3://easyluxury s3://easyluxury-prod \
  --source-region us-east-1 \
  --region us-east-1 \
  --endpoint-url http://localhost:9000
```

### From AWS S3 to MinIO:
```bash
aws s3 sync s3://easyluxury-prod s3://easyluxury \
  --region us-east-1 \
  --endpoint-url http://localhost:9000
```

---

## 📊 Monitoring & Costs

### Development (MinIO):
- **Cost:** Free (runs locally)
- **Storage:** Limited by disk space
- **Performance:** Excellent (local)

### Production (AWS S3):
- **Storage:** ~$0.023/GB/month (Standard)
- **Requests:** $0.0004/1000 PUT, $0.0004/1000 GET
- **Data Transfer:** First 100GB free/month, then $0.09/GB
- **Estimate:** ~$10-50/month for 100GB storage + 10K requests

**Enable AWS Cost Explorer for tracking!**

---

## ✅ Configuration Checklist

### Before Running Application:
- [ ] MinIO/S3 service is running
- [ ] Bucket `easyluxury` (or custom name) is created
- [ ] Environment variables are set (or using defaults)
- [ ] CORS is configured (for direct uploads)
- [ ] Backend starts without S3-related errors
- [ ] Test presigned URL generation
- [ ] Test file upload from frontend

---

## 🆘 Need Help?

**Check Configuration:**
```bash
# In backend directory
./mvnw spring-boot:run -Ddebug

# Look for S3Config initialization logs
```

**Test MinIO Manually:**
```bash
# List buckets
mc ls local/

# Check bucket contents
mc ls local/easyluxury/
```

**Test AWS S3 Manually:**
```bash
# List buckets
aws s3 ls

# Check bucket contents
aws s3 ls s3://easyluxury-prod/
```

---

**Status:** ✅ Configuration guide complete  
**Last Updated:** 2025-10-11  
**Next Step:** Create the `easyluxury` bucket in MinIO before first property upload!
