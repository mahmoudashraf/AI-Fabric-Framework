# 📋 Supabase Setup Checklist

## Current Configuration

✅ **Credentials Configured**
- Project: `easy-luxury`
- URL: `https://vfcypwztvtgurooszvtf.supabase.co`
- Keys: Configured in backend and frontend

---

## ⚠️ Important: Enable Email Authentication

Before users can register/login, you need to enable email authentication in Supabase:

### Step 1: Go to Authentication Settings

1. Open your Supabase project: https://supabase.com/dashboard/project/vfcypwztvtgurooszvtf
2. Click **Authentication** in the left sidebar
3. Click **Providers** tab

### Step 2: Enable Email Provider

1. Find **Email** in the providers list
2. Make sure it's **ENABLED** (should be enabled by default)
3. Check these settings:
   - ✅ **Enable email provider**
   - ✅ **Confirm email** (Optional - can disable for development)

### Step 3: Configure Email Templates (Optional)

1. Go to **Authentication** → **Email Templates**
2. You can customize:
   - Confirmation email
   - Reset password email
   - Magic link email

For development, you can use the default templates.

### Step 4: Disable Email Confirmation (For Development)

To make testing easier, you can disable email confirmation:

1. Go to **Authentication** → **Providers**
2. Click on **Email**
3. **Uncheck** "Enable email confirmations"
4. Click **Save**

This allows users to register and login immediately without verifying their email.

---

## 🔒 Row Level Security (RLS)

Your backend handles all security, so you DON'T need to set up RLS in Supabase.

**Why?** Because:
- Supabase is ONLY used for authentication (login/register)
- Your Spring Boot backend handles all data access
- Your PostgreSQL database is separate from Supabase
- The backend verifies JWT tokens from Supabase

**What Supabase stores:**
- User email addresses
- Hashed passwords
- Authentication sessions

**What YOUR database stores:**
- User profiles (name, role)
- Agencies, properties, bookings
- All business data

---

## 🧪 Test Authentication

After enabling email auth, test it:

### 1. Start Your App
```bash
./run.sh
```

### 2. Try to Register
Go to: http://localhost:3000/register
- Enter email and password
- Submit form

### 3. Check Supabase Dashboard
Go to: https://supabase.com/dashboard/project/vfcypwztvtgurooszvtf/auth/users
- You should see the new user
- Note the user's UUID

### 4. Login
Go to: http://localhost:3000/login
- Login with same credentials
- You should be redirected to dashboard

### 5. Check Your Database
```bash
psql -U postgres -d easyluxury
SELECT * FROM users;
```
- You should see a user with the `supabase_id` matching the UUID from Supabase

---

## 🎨 Optional: Enable OAuth Providers

If you want Google/Facebook/Apple login:

### Google OAuth
1. Go to **Authentication** → **Providers**
2. Click **Google**
3. Enable it
4. Follow the setup guide to get Google credentials
5. Add authorized redirect URL: `https://vfcypwztvtgurooszvtf.supabase.co/auth/v1/callback`

### Facebook OAuth
1. Go to **Authentication** → **Providers**
2. Click **Facebook**
3. Enable it
4. Get Facebook App ID and Secret
5. Add authorized redirect URL

### Apple OAuth
1. Go to **Authentication** → **Providers**
2. Click **Apple**
3. Enable it
4. Get Apple credentials
5. Add authorized redirect URL

---

## 📧 Email Settings for Production

For production, you'll want to configure email sending:

1. Go to **Project Settings** → **Auth**
2. Scroll to **SMTP Settings**
3. Configure your email provider (SendGrid, AWS SES, etc.)

For development, Supabase uses their own email service (limited to 3 emails/hour in free tier).

---

## 🔐 Security Best Practices

### For Development
- ✅ Disable email confirmation
- ✅ Use simple passwords for testing
- ✅ Keep service_role key secret

### For Production
- ✅ Enable email confirmation
- ✅ Enable ReCAPTCHA
- ✅ Enable rate limiting
- ✅ Configure custom SMTP
- ✅ Set up password policies
- ✅ Enable MFA (Multi-Factor Authentication)

---

## ✅ Configuration Summary

**What's Configured:**
- [x] Supabase project created (easy-luxury)
- [x] API keys set in backend/.env
- [x] API keys set in frontend/.env.local
- [x] JWT verification configured in backend

**What You Should Check:**
- [ ] Email provider is enabled in Supabase
- [ ] Email confirmation disabled (for easier development)
- [ ] Test user registration works
- [ ] Test user login works
- [ ] Verify JWT tokens work with backend

**Quick Check:**
1. Go to: https://supabase.com/dashboard/project/vfcypwztvtgurooszvtf/auth/providers
2. Verify **Email** is enabled
3. Disable "Confirm email" for development

---

## 🚨 Common Issues

### Issue: "Email not authorized"
**Solution:** Make sure email provider is enabled in Supabase dashboard

### Issue: "User needs to verify email"
**Solution:** Disable email confirmation in Supabase → Auth → Providers → Email

### Issue: "Invalid JWT token"
**Solution:** Check that `SUPABASE_URL` in backend/.env matches your project URL

### Issue: "User created in Supabase but not in database"
**Solution:** This is normal! The user is created in your database on first API call after login.

---

## 🎯 Next Steps

1. ✅ Credentials are configured
2. ⚠️ Check Supabase dashboard - enable email auth
3. ⚠️ Install Maven: `sudo apt install maven`
4. ⚠️ Start PostgreSQL: `docker run -d --name easyluxury-db -e POSTGRES_DB=easyluxury -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:14`
5. 🚀 Run the app: `./run.sh`

---

**Your Supabase project is configured and ready!** ✅
