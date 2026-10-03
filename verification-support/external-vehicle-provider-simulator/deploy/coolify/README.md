# Coolify Deployment

Deploy this verification-only service from the monorepo root.

```text
Base directory: /
Dockerfile: /verification-support/external-vehicle-provider-simulator/deploy/container/Dockerfile
Exposed port: 8110
Health path: /actuator/health/readiness
Persistent volume: /app/data
```

Required runtime-only environment variables:

```text
PORT=8110
SIMULATOR_CONTROL_API_KEY=<strong operator-only value>
SIMULATOR_TOKEN_SIGNING_SECRET=<strong independent signing value>
SIMULATOR_PROFILE_A_ACCOUNT_ID=<opaque fixture account>
SIMULATOR_PROFILE_A_KEY=<profile-a credential key>
SIMULATOR_PROFILE_A_SECRET=<profile-a credential secret>
SIMULATOR_PROFILE_A_WEBHOOK_SECRET=<profile-a event secret>
SIMULATOR_PROFILE_B_ACCOUNT_ID=<different opaque fixture account>
SIMULATOR_PROFILE_B_API_KEY=<profile-b API key>
SIMULATOR_PROFILE_B_WEBHOOK_SECRET=<profile-b event secret>
SIMULATOR_AUTOTRADER_ADVERTISER_ID=<synthetic numeric advertiser ID>
SIMULATOR_AUTOTRADER_API_KEY=<synthetic API key>
SIMULATOR_AUTOTRADER_API_SECRET=<synthetic API secret>
SIMULATOR_AUTOTRADER_NOTIFICATION_SECRET=<independent synthetic notification secret>
SIMULATOR_AUTOTRADER_INTEGRATION_ID=<synthetic integration identifier>
SIMULATOR_ALLOWED_WEBHOOK_HOSTS=<comma-separated exact hosts or *.suffix patterns>
SIMULATOR_DATA_PATH=/app/data/vehicle-provider
JAVA_OPTS=-Xms128m -Xmx384m
```

Do not expose `SIMULATOR_CONTROL_API_KEY` to a deployment, browser, model, or
customer application. Provider-profile credentials and webhook secrets are
bound only to their corresponding temporary canary deployment. Auto Trader
profile values must be synthetic and must never reuse sandbox/production
credentials, advertiser IDs, inventory, or notification secrets.
