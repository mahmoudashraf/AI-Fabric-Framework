# 📧 Supabase Email Configuration Guide

## Current Issue: Email Bounce Backs

Your Supabase project is experiencing email bounce backs, which can lead to:
- Email sending privileges being revoked
- Users unable to receive authentication emails
- Poor user experience

## ✅ Immediate Fixes Applied

1. **Increased Email Rate Limit**: From 2 to 10 emails per hour
2. **Improved Email Validation**: Better regex patterns to prevent invalid emails
3. **Enhanced Error Handling**: Better user feedback for registration issues

## 🔧 Production SMTP Configuration

### Option 1: SendGrid (Recommended)

1. **Sign up for SendGrid**:
   - Go to [sendgrid.com](https://sendgrid.com)
   - Create a free account (100 emails/day free)

2. **Get API Key**:
   - Go to Settings → API Keys
   - Create a new API key with "Mail Send" permissions
   - Copy the API key

3. **Configure Supabase**:
   ```toml
   # In your supabase/config.toml
   [auth.email.smtp]
   enabled = true
   host = "smtp.sendgrid.net"
   port = 587
   user = "apikey"
   pass = "env(SENDGRID_API_KEY)"
   admin_email = "noreply@yourdomain.com"
   sender_name = "Easy Luxury"
   ```

4. **Set Environment Variable**:
   ```bash
   export SENDGRID_API_KEY="your_sendgrid_api_key_here"
   ```

### Option 2: AWS SES (Cost-Effective)

1. **Set up AWS SES**:
   - Go to AWS Console → Simple Email Service
   - Verify your domain or email address
   - Request production access (if needed)

2. **Get SMTP Credentials**:
   - Go to SES → SMTP Settings
   - Create SMTP credentials
   - Note the username and password

3. **Configure Supabase**:
   ```toml
   [auth.email.smtp]
   enabled = true
   host = "email-smtp.us-east-1.amazonaws.com"  # Use your region
   port = 587
   user = "your_smtp_username"
   pass = "env(AWS_SES_SMTP_PASSWORD)"
   admin_email = "noreply@yourdomain.com"
   sender_name = "Easy Luxury"
   ```

### Option 3: Mailgun

1. **Sign up for Mailgun**:
   - Go to [mailgun.com](https://mailgun.com)
   - Create account (10,000 emails/month free)

2. **Get SMTP Credentials**:
   - Go to Sending → Domains
   - Select your domain → SMTP
   - Copy credentials

3. **Configure Supabase**:
   ```toml
   [auth.email.smtp]
   enabled = true
   host = "smtp.mailgun.org"
   port = 587
   user = "postmaster@yourdomain.mailgun.org"
   pass = "env(MAILGUN_SMTP_PASSWORD)"
   admin_email = "noreply@yourdomain.com"
   sender_name = "Easy Luxury"
   ```

## 🚀 Quick Setup for Development

For immediate testing, you can use a simple SMTP service:

### Using Gmail (Not Recommended for Production)

1. **Enable 2FA on Gmail**
2. **Generate App Password**:
   - Go to Google Account → Security → App passwords
   - Generate password for "Mail"

3. **Configure Supabase**:
   ```toml
   [auth.email.smtp]
   enabled = true
   host = "smtp.gmail.com"
   port = 587
   user = "your_email@gmail.com"
   pass = "env(GMAIL_APP_PASSWORD)"
   admin_email = "your_email@gmail.com"
   sender_name = "Easy Luxury"
   ```

## 📋 Environment Variables Setup

Create a `.env.local` file in your frontend directory:

```bash
# For SendGrid
SENDGRID_API_KEY=your_sendgrid_api_key

# For AWS SES
AWS_SES_SMTP_PASSWORD=your_smtp_password

# For Mailgun
MAILGUN_SMTP_PASSWORD=your_smtp_password

# For Gmail (development only)
GMAIL_APP_PASSWORD=your_app_password
```

## 🔄 Apply Configuration

After updating your config:

1. **Restart Supabase**:
   ```bash
   cd frontend
   supabase stop
   supabase start
   ```

2. **Test Email Sending**:
   - Try registering a new user
   - Check if confirmation email is sent
   - Monitor Supabase logs for errors

## 🧹 Clean Up Invalid Users

1. **Go to Supabase Dashboard**:
   - Visit: https://supabase.com/dashboard/project/vfcypwztvtgurooszvtf/auth/users

2. **Identify Invalid Users**:
   - Look for users with invalid email formats
   - Check for users who never confirmed their email

3. **Delete Invalid Users**:
   - Select users with invalid emails
   - Click "Delete" (be careful not to delete valid users)

## 📊 Monitor Email Health

1. **Check Supabase Dashboard**:
   - Go to Authentication → Users
   - Look for bounce indicators

2. **Monitor Email Provider Dashboard**:
   - Check bounce rates
   - Review delivery statistics
   - Set up alerts for high bounce rates

## 🚨 Emergency Recovery

If your email privileges are revoked:

1. **Contact Supabase Support**:
   - Explain the situation
   - Show evidence of fixing bounce issues
   - Request privilege restoration

2. **Implement Temporary Workaround**:
   - Disable email confirmation temporarily
   - Use OAuth providers (Google, Facebook)
   - Implement manual user verification

## 📈 Best Practices

1. **Email Validation**:
   - ✅ Use robust regex patterns
   - ✅ Check email length limits
   - ✅ Validate domain existence (optional)

2. **Rate Limiting**:
   - ✅ Set appropriate limits
   - ✅ Monitor usage patterns
   - ✅ Implement exponential backoff

3. **Monitoring**:
   - ✅ Track bounce rates
   - ✅ Monitor delivery success
   - ✅ Set up alerts

4. **User Experience**:
   - ✅ Clear error messages
   - ✅ Resend email functionality
   - ✅ Alternative authentication methods

## 🔗 Additional Resources

- [Supabase Auth Documentation](https://supabase.com/docs/guides/auth)
- [SendGrid Integration Guide](https://supabase.com/docs/guides/auth/auth-email-smtp)
- [AWS SES Setup](https://docs.aws.amazon.com/ses/)
- [Email Best Practices](https://supabase.com/docs/guides/auth/auth-email-smtp#best-practices)
