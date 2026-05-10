#!/usr/bin/env bash
set -euo pipefail

npm --prefix Real_Apps/loomai-landing-site run smoke
npm --prefix Real_Apps/loomai-landing-site run smoke:browser
