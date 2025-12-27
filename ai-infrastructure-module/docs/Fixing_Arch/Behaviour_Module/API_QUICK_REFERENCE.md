# Behavior Processing API - Quick Reference Card

**Version:** 2.0.0  
**Last Updated:** 2025-12-27

---

## 🚀 PROCESSING ENDPOINTS

### 1. Analyze Single User

```bash
POST /api/behavior/processing/users/{userId}
```

**Example:**
```bash
curl -X POST http://localhost:8080/api/behavior/processing/users/550e8400-e29b-41d4-a716-446655440000
```

**Response:** `BehaviorInsights` object

---

### 2. Batch Processing (Flexible)

```bash
POST /api/behavior/processing/batch
Content-Type: application/json

{
  "maxUsers": 200,              // Optional: How many users
  "maxDurationMinutes": 10,     // Optional: How long to run
  "delayBetweenUsersMs": 100    // Optional: Throttling
}
```

**Examples:**
```bash
# Process next 50 users
curl -X POST http://localhost:8080/api/behavior/processing/batch \
  -H "Content-Type: application/json" \
  -d '{"maxUsers": 50}'

# Process for 5 minutes (as many as possible)
curl -X POST http://localhost:8080/api/behavior/processing/batch \
  -H "Content-Type: application/json" \
  -d '{"maxDurationMinutes": 5}'

# Fast processing (10ms delay)
curl -X POST http://localhost:8080/api/behavior/processing/batch \
  -H "Content-Type: application/json" \
  -d '{"maxUsers": 100, "delayBetweenUsersMs": 10}'
```

**Response:**
```json
{
  "status": "COMPLETED",
  "processedCount": 50,
  "successCount": 48,
  "errorCount": 2,
  "durationMs": 12543,
  "startedAt": "2025-12-27T10:00:00",
  "completedAt": "2025-12-27T10:00:12"
}
```

---

### 3. Continuous Processing (Background Job)

```bash
POST /api/behavior/processing/continuous
Content-Type: application/json

{
  "usersPerBatch": 100,         // Users per iteration
  "intervalMinutes": 5,         // Time between iterations
  "maxIterations": 20           // null or 0 = infinite
}
```

**Example:**
```bash
# Process 100 users every 5 minutes, 20 times
curl -X POST http://localhost:8080/api/behavior/processing/continuous \
  -H "Content-Type: application/json" \
  -d '{
    "usersPerBatch": 100,
    "intervalMinutes": 5,
    "maxIterations": 20
  }'
```

**Response:**
```json
{
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "STARTED",
  "message": "Continuous processing job submitted in background"
}
```

---

## 🎛️ JOB MANAGEMENT ENDPOINTS

### 4. Cancel Continuous Job

```bash
DELETE /api/behavior/processing/continuous/{jobId}
```

**Example:**
```bash
curl -X DELETE http://localhost:8080/api/behavior/processing/continuous/a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

**Response:**
```json
{
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "success": true,
  "message": "Job cancelled successfully",
  "currentStatus": "CANCELLED",
  "processedBeforeCancellation": 347
}
```

---

### 5. Get Job Status

```bash
GET /api/behavior/processing/continuous/{jobId}/status
```

**Example:**
```bash
curl http://localhost:8080/api/behavior/processing/continuous/a1b2c3d4-e5f6-7890-abcd-ef1234567890/status
```

**Response:**
```json
{
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "RUNNING",
  "currentIteration": 12,
  "maxIterations": 20,
  "totalProcessed": 1200,
  "startedAt": "2025-12-27T10:00:00",
  "completedAt": null,
  "error": null
}
```

---

### 6. List All Continuous Jobs

```bash
GET /api/behavior/processing/continuous/jobs
```

**Example:**
```bash
curl http://localhost:8080/api/behavior/processing/continuous/jobs
```

**Response:**
```json
[
  {
    "jobId": "job-1",
    "status": "RUNNING",
    "currentIteration": 5,
    "totalProcessed": 500,
    "startedAt": "2025-12-27T10:00:00"
  },
  {
    "jobId": "job-2",
    "status": "COMPLETED",
    "totalProcessed": 2000,
    "startedAt": "2025-12-27T09:00:00",
    "completedAt": "2025-12-27T09:45:00"
  }
]
```

---

## ⏸️ SCHEDULED PROCESSING CONTROL

### 7. Pause Scheduled Processing

```bash
POST /api/behavior/processing/scheduled/pause
```

**Example:**
```bash
curl -X POST http://localhost:8080/api/behavior/processing/scheduled/pause
```

**Response:**
```json
{
  "action": "PAUSED",
  "message": "Scheduled processing paused. Worker will skip processing until resumed.",
  "paused": true,
  "timestamp": "2025-12-27T10:30:00"
}
```

**Effect:** Next scheduled run will be skipped. Worker continues running but does nothing.

---

### 8. Resume Scheduled Processing

```bash
POST /api/behavior/processing/scheduled/resume
```

**Example:**
```bash
curl -X POST http://localhost:8080/api/behavior/processing/scheduled/resume
```

**Response:**
```json
{
  "action": "RESUMED",
  "message": "Scheduled processing resumed. Worker will process on next schedule.",
  "paused": false,
  "timestamp": "2025-12-27T11:00:00"
}
```

---

### 9. Check Scheduled Status

```bash
GET /api/behavior/processing/scheduled/status
```

**Example:**
```bash
curl http://localhost:8080/api/behavior/processing/scheduled/status
```

**Response:**
```json
{
  "enabled": true,
  "paused": false,
  "scheduleCron": "0 */15 * * * *",
  "batchSize": 100,
  "message": "Scheduled processing is ACTIVE"
}
```

**States:**
- `enabled=true, paused=false` → **ACTIVE** (processing normally)
- `enabled=true, paused=true` → **PAUSED** (worker skipping)
- `enabled=false` → **DISABLED** (worker not running)

---

## 📊 COMPLETE WORKFLOW EXAMPLES

### Workflow 1: Start, Monitor, and Cancel Continuous Job

```bash
#!/bin/bash

# 1. Start continuous job
echo "Starting continuous job..."
JOB_RESPONSE=$(curl -s -X POST http://localhost:8080/api/behavior/processing/continuous \
  -H "Content-Type: application/json" \
  -d '{
    "usersPerBatch": 100,
    "intervalMinutes": 5,
    "maxIterations": 20
  }')

JOB_ID=$(echo $JOB_RESPONSE | jq -r '.jobId')
echo "Job started: $JOB_ID"

# 2. Monitor progress
for i in {1..10}; do
  echo "Checking status (attempt $i)..."
  STATUS=$(curl -s http://localhost:8080/api/behavior/processing/continuous/$JOB_ID/status)
  
  CURRENT_ITER=$(echo $STATUS | jq -r '.currentIteration')
  TOTAL=$(echo $STATUS | jq -r '.totalProcessed')
  JOB_STATUS=$(echo $STATUS | jq -r '.status')
  
  echo "Status: $JOB_STATUS | Iteration: $CURRENT_ITER/20 | Processed: $TOTAL users"
  
  # Cancel if too many errors detected
  if [ "$TOTAL" -lt 100 ] && [ "$CURRENT_ITER" -gt 5 ]; then
    echo "ERROR: Low throughput detected. Cancelling job..."
    curl -s -X DELETE http://localhost:8080/api/behavior/processing/continuous/$JOB_ID
    break
  fi
  
  sleep 30
done

# 3. Final status
echo "Final job status:"
curl -s http://localhost:8080/api/behavior/processing/continuous/$JOB_ID/status | jq
```

---

### Workflow 2: Maintenance Window with Pause/Resume

```bash
#!/bin/bash

echo "=== Starting Maintenance ==="

# 1. Check current scheduled status
echo "Current status:"
curl -s http://localhost:8080/api/behavior/processing/scheduled/status | jq

# 2. Pause scheduled processing
echo "Pausing scheduled processing..."
curl -s -X POST http://localhost:8080/api/behavior/processing/scheduled/pause | jq

# 3. Perform maintenance
echo "Performing maintenance..."
# Database upgrade, config changes, etc.
sleep 60

# 4. Resume scheduled processing
echo "Resuming scheduled processing..."
curl -s -X POST http://localhost:8080/api/behavior/processing/scheduled/resume | jq

# 5. Verify resumed
echo "Verification:"
curl -s http://localhost:8080/api/behavior/processing/scheduled/status | jq

echo "=== Maintenance Complete ==="
```

---

### Workflow 3: Clean Up Old Jobs

```bash
#!/bin/bash

echo "Listing all continuous jobs..."
JOBS=$(curl -s http://localhost:8080/api/behavior/processing/continuous/jobs)

echo "Active jobs:"
echo $JOBS | jq '.[] | select(.status=="RUNNING")'

echo "Completed jobs:"
echo $JOBS | jq '.[] | select(.status=="COMPLETED")'

echo "Cancelled/Failed jobs:"
echo $JOBS | jq '.[] | select(.status=="CANCELLED" or .status=="FAILED")'

# Cancel all running jobs (emergency stop)
echo "Cancelling all running jobs..."
echo $JOBS | jq -r '.[] | select(.status=="RUNNING") | .jobId' | while read jobId; do
  echo "Cancelling: $jobId"
  curl -s -X DELETE http://localhost:8080/api/behavior/processing/continuous/$jobId | jq
done
```

---

## 🔑 KEY CAPABILITIES SUMMARY

### ✅ What You Can Do

| Capability | Endpoint | Use Case |
|------------|----------|----------|
| **Process specific user** | `POST /users/{userId}` | Support tickets, VIP users |
| **Process batch** | `POST /batch` | Manual catch-up, testing |
| **Start background job** | `POST /continuous` | Migrations, bulk processing |
| **Cancel background job** | `DELETE /continuous/{jobId}` | Stop runaway jobs, errors detected |
| **Monitor job progress** | `GET /continuous/{jobId}/status` | Track migration progress |
| **List all jobs** | `GET /continuous/jobs` | Dashboard, monitoring |
| **Pause scheduled** | `POST /scheduled/pause` | Maintenance windows |
| **Resume scheduled** | `POST /scheduled/resume` | After maintenance |
| **Check scheduled state** | `GET /scheduled/status` | Verify worker status |

---

**NOTE FOR IMPLEMENTATION SESSION:**
All endpoints include complete code in BEHAVIOR_PROCESSING_SCHEDULER_IMPLEMENTATION.md (v2.0.0).
Copy-paste ready, includes job tracking, cancellation, and pause/resume logic.

