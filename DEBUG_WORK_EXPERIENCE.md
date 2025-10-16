# 🔍 Debug Work Experience Data

## Issue
Seeing mock companies ("Tech Corp", "StartupXYZ") instead of real data from CV.

---

## Root Cause Analysis

### The Components ARE Getting Real Data From Database

✅ **Data Flow is Correct:**
```
Profile.tsx
  ↓ useQuery(['aiProfile', 'latest'])
  ↓ aiProfileApi.getLatestProfile()
  ↓ GET /api/ai-profile/latest
  ↓ Returns: ai_profiles table, latest COMPLETE record
  ↓ aiProfileApi.parseAiAttributes(aiAttributes)
  ↓ JSON.parse(aiAttributes)
  ↓ Returns AIProfileData with companies[]
  ↓ Display: aiProfile.companies.map(...)
```

### The Problem: Old Mock Data in Database

❌ **Database contains outdated profile:**

When profile was **first generated** (before OpenAI was properly configured):
1. Backend called `aiService.generateProfileFromCV()`
2. OpenAI API call failed (invalid key)
3. Fallback to mock data (lines 77-80 in AIService.java)
4. Mock data saved to database
5. Even after fixing OpenAI → old mock data still in DB

---

## 🧪 How to Verify

### Step 1: Check Browser Console

I've added debug logging. Open browser DevTools Console and look for:

```javascript
📊 [Profile.tsx] Raw profile from API: {...}
📊 [Profile.tsx] Parsed AI profile data: {...}
📊 [Profile.tsx] Companies in profile: [...]
```

**If you see:**
```javascript
companies: [
  { name: "Tech Corp", position: "Senior Software Engineer", ... },
  { name: "StartupXYZ", position: "Software Engineer", ... }
]
```
→ **You have OLD mock data in database!**

### Step 2: Generate New Profile

1. Go to "Generate with AI" tab
2. Paste your CV or upload file
3. Click "Generate Profile"
4. Watch console for:
```javascript
🤖 [AIProfileTab] Profile generated from backend: {...}
🤖 [AIProfileTab] Parsed profile data: {...}
🤖 [AIProfileTab] Companies extracted: [...]
🤖 [AIProfileTab] Number of companies: X
```

**If you see real company names from YOUR CV:**
→ OpenAI is working! ✅

**If you still see "Tech Corp" and "StartupXYZ":**
→ OpenAI is NOT working (check backend logs) ❌

### Step 3: Publish New Profile

After generating with real data:
1. Upload photos
2. Click "Publish Profile"
3. Navigate to "Profile" tab
4. Check if Work Experience shows YOUR companies

---

## 🔧 Solutions

### Solution 1: Generate Fresh Profile (Recommended)

**If OpenAI is now configured correctly:**

1. Delete old profile from database:
   ```sql
   -- Connect to PostgreSQL
   DELETE FROM ai_profiles WHERE user_id = 'YOUR_USER_ID';
   ```

2. Generate new profile:
   - Go to "Generate with AI" tab
   - Upload your CV
   - Generate new profile
   - Publish

3. Verify in Profile tab:
   - Should show YOUR real companies
   - Not "Tech Corp" / "StartupXYZ"

### Solution 2: Verify Backend is Using Real OpenAI

**Check backend logs when generating:**

```bash
# Start backend and watch logs
cd backend && mvn spring-boot:run

# Look for:
[INFO] Generating AI profile for user...
[DEBUG] OpenAI response: {...}

# If you see:
[WARN] OpenAI API key not configured, using mock data
→ OpenAI is NOT working!

# If you see actual response from OpenAI:
→ OpenAI IS working! Just need to generate new profile.
```

### Solution 3: Force Clear React Query Cache

**If new profile is generated but old data still shows:**

```javascript
// In browser console:
localStorage.clear();
location.reload();
```

---

## 📊 Expected vs Actual

### Mock Data (What You're Seeing Now):
```json
{
  "name": "John Doe",
  "jobTitle": "Senior Software Engineer",
  "companies": [
    {
      "name": "Tech Corp",
      "position": "Senior Software Engineer",
      "duration": "2020-2024"
    },
    {
      "name": "StartupXYZ",
      "position": "Software Engineer",
      "duration": "2018-2020"
    }
  ]
}
```

### Real Data (What You Should See):
```json
{
  "name": "[Your Real Name from CV]",
  "jobTitle": "[Your Real Job Title]",
  "companies": [
    {
      "name": "[Your Actual Company 1]",
      "position": "[Your Actual Position]",
      "duration": "[Your Actual Dates]"
    },
    {
      "name": "[Your Actual Company 2]",
      "position": "[Your Actual Position]",
      "duration": "[Your Actual Dates]"
    }
  ]
}
```

---

## ✅ Verification Checklist

- [ ] Check browser console for debug logs
- [ ] Verify companies in console match "Tech Corp" / "StartupXYZ"
- [ ] Generate NEW profile from CV
- [ ] Check console during generation for real company names
- [ ] Publish the new profile
- [ ] Navigate to Profile tab
- [ ] Verify Work Experience shows YOUR companies
- [ ] If still showing mock data → check backend logs

---

## 🎯 Quick Test

**Open browser console and run:**
```javascript
// Check what's in React Query cache
window.__REACT_QUERY_DEVTOOLS__ // or just check console logs
```

**Then generate a new profile and watch for:**
- 🤖 AIProfileTab logs during generation
- 📊 Profile.tsx logs when viewing profile
- Check if company names are real or mock

---

## 📞 Next Steps

1. **Open browser console** (F12)
2. **Navigate to "Generate with AI" tab**
3. **Generate profile** (paste CV or upload file)
4. **Watch console logs** - do you see real company names?
5. **If YES** → Publish and check Profile tab
6. **If NO** → Check backend logs for OpenAI errors

---

*Debug logging has been added to help identify the issue!*
