# Platfrom Workspace

This workspace contains the first implementation scaffold for the configurable AI enablement platform described in:

- `changes/Productization/CONFIGURABLE_AI_ENABLEMENT_PLATFORM_PLAN.md`

It is intentionally split into:

- `backend`: control-plane backend service
- `ui`: control-plane web console

V1 goals for this workspace:

- establish the backend and UI foundations
- keep runtime and REST connector as separate immutable deployment targets
- build the control plane incrementally, phase by phase

