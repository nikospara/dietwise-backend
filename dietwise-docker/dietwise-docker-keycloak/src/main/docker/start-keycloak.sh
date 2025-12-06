#!/bin/bash

if [ -f /opt/keycloak/bin/DIETWISE-REALM-IMPORTED ]; then
	exec /opt/keycloak/bin/kc.sh --verbose start-dev $@
else
	touch /opt/keycloak/bin/DIETWISE-REALM-IMPORTED
	exec /opt/keycloak/bin/kc.sh --verbose start-dev --import-realm $@
fi
