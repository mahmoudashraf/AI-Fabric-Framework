#!/bin/sh
set -eu
umask 077
exec </dev/null
printf '%s\n' "${SSH_ORIGINAL_COMMAND-}" | /usr/bin/sudo -n -- /usr/bin/python3 -I /usr/local/libexec/loomai-retirement-read-only-adapter.py
