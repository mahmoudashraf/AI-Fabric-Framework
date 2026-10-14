# LoomAI Engage Standalone AI Workspace

One shared, static frontend shell for verified LoomAI AI Workspace installations. The shell resolves its exact browser origin through the Platform, initializes the reviewed experience pack, and sends message traffic directly to the installation's assigned runtime.

Required runtime environment:

- `PORT` (default `8080`)
- `PLATFORM_API_BASE_URL` (for example `https://api.loomai.pro`)

Customer identity configuration is obtained from the installation bootstrap. Secret provider credentials are never placed in this image or in an experience pack.
