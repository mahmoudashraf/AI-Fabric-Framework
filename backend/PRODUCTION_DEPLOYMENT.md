# Production Deployment Guide

## Overview
This guide covers deploying the EasyLuxury backend to production using the `application-prod.yml` configuration.

## Prerequisites
- Java 21+ installed
- PostgreSQL database
- AWS S3 bucket or compatible object storage
- Supabase project configured

## Configuration Files Created

### 1. `application-prod.yml`
Production-specific Spring Boot configuration with:
- Optimized database connection pooling
- Production logging configuration
- Security settings
- Performance optimizations
- Monitoring and metrics

### 2. `env.prod.template`
Template for environment variables. Copy to `.env.prod` and fill in your values.

## Environment Variables Required

### Database
```bash
DATABASE_URL=jdbc:postgresql://your-prod-db-host:5432/easyluxury
DATABASE_USERNAME=your_prod_db_user
DATABASE_PASSWORD=your_prod_db_password
```

### Supabase
```bash
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_API_KEY=your_production_anon_key
SUPABASE_SERVICE_ROLE_KEY=your_production_service_role_key
```

### AWS S3
```bash
AWS_S3_ENDPOINT=https://s3.amazonaws.com
AWS_S3_REGION=us-east-1
AWS_S3_BUCKET=your-production-bucket
AWS_ACCESS_KEY=your_aws_access_key
AWS_SECRET_KEY=your_aws_secret_key
```

### Server
```bash
SERVER_PORT=8080
REQUIRE_SSL=true
CORS_ALLOWED_ORIGINS=https://easyluxury.com,https://www.easyluxury.com
```

## Deployment Steps

### 1. Prepare Environment
```bash
# Copy environment template
cp env.prod.template .env.prod

# Edit with your production values
nano .env.prod
```

### 2. Build Application
```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package for production
mvn package -DskipTests
```

### 3. Deploy
```bash
# Run with production profile
java -jar target/easyluxury-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod

# Or with environment file
java -jar target/easyluxury-backend-0.0.1-SNAPSHOT.jar --spring.config.location=file:.env.prod
```

### 4. Docker Deployment
```bash
# Build Docker image
docker build -t easyluxury-backend:prod .

# Run with environment variables
docker run -d \
  --name easyluxury-backend \
  -p 8080:8080 \
  --env-file .env.prod \
  easyluxury-backend:prod
```

## Production Features

### Security
- Mock authentication disabled
- SSL/TLS support
- CORS configuration
- Error details hidden from clients

### Performance
- Connection pooling optimized (20 max connections)
- HTTP/2 enabled
- Compression enabled
- Batch processing optimized

### Monitoring
- Health checks at `/actuator/health`
- Metrics at `/actuator/metrics`
- Prometheus metrics enabled
- Structured logging

### Logging
- Log files rotated (100MB max, 30 days retention)
- Log levels optimized for production
- No sensitive data in logs

## Health Checks

### Application Health
```bash
curl http://localhost:8080/actuator/health
```

### Database Health
```bash
curl http://localhost:8080/actuator/health/db
```

### Custom Health Checks
The application includes custom health checks for:
- Database connectivity
- Supabase connectivity
- S3 connectivity

## Monitoring

### Metrics
- Application metrics: `/actuator/metrics`
- Prometheus format: `/actuator/prometheus`
- Custom business metrics included

### Logs
- Application logs: `/var/log/easyluxury/backend.log`
- Access logs: Configured in server settings
- Error logs: Separate error tracking

## Troubleshooting

### Common Issues
1. **Database Connection**: Check DATABASE_URL and credentials
2. **Supabase Auth**: Verify API keys and URL
3. **S3 Access**: Confirm AWS credentials and bucket permissions
4. **Port Conflicts**: Ensure SERVER_PORT is available

### Debug Mode
To enable debug logging temporarily:
```bash
java -jar app.jar --logging.level.com.easyluxury=DEBUG
```

## Security Considerations

1. **Environment Variables**: Never commit `.env.prod` to version control
2. **Database**: Use strong passwords and limit access
3. **API Keys**: Rotate Supabase keys regularly
4. **SSL**: Enable SSL in production
5. **CORS**: Restrict allowed origins to your domains only

## Backup and Recovery

### Database Backup
```bash
pg_dump -h your-db-host -U your-user easyluxury > backup.sql
```

### Application Backup
- Backup JAR file
- Backup configuration files
- Backup environment variables (securely)

## Scaling Considerations

### Horizontal Scaling
- Use load balancer
- Configure sticky sessions if needed
- Database connection pooling limits

### Vertical Scaling
- Adjust HikariCP pool size based on CPU cores
- Monitor memory usage
- Tune JVM parameters

## Maintenance

### Updates
1. Build new version
2. Run database migrations
3. Deploy with zero downtime
4. Monitor health checks

### Monitoring
- Set up alerts for health check failures
- Monitor response times
- Track error rates
- Monitor resource usage
