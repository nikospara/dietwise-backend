# DietWise Keycloak

This project builds the Docker image for the Keycloak IDM of DietWise.

> **NOTE/WARNING:** As of the date of this writing, the Docker images are for development purposes only!

## Building

## Configuring

## Recreating the dev Realm

Login as user `admin`/`admin`. (Credentials configured in
`dietwise-docker/dietwise-docker-keycloak/src/main/docker/Dockerfile`,
`KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD`).

### Create the Realm

Create a new enabled realm, "dietwise". Go to "Configure" from the left menu:

- Realm Settings:
	- General
		- Display name: "DietWise"
		- Save
	- Login
		- User registration, Forgot password, Remember me: On
		- Email as username, Login with email: On
		- Verify email: **should be On, TODO**
	- Events: **TODO, for when we implement the user management events**
	- Sessions: Make sure "Offline session settings" -> "Offline Session Idle" is set to something like 30 Days
- Realm roles:
    - Create Role: "influencer"

### Create a client for RecipeWatch

Create a new client in the Realm:

- General settings:
	- Client type: OpenID Connect
	- Client ID: recipewatch
	- Name: RecipeWatch
- Capability config:
	- Client authentication: Off
	- Authorization: Off
	- Authentication flow: Check *only* "Standard flow"
    - PKCE Method: **TODO** (should be S256 - also in the "Settings" tab, under the "Capability config" section)
- Login settings:
	- Root URL: **TODO**
	- Home URL: **TODO**
	- Valid redirect URIs:
		- http://localhost:5173/authcallback
		- http://localhost:5173/recipewatch/authcallback
		- https://gaia.ispatial.survey.ntua.gr/recipewatch/authcallback
		- **TODO** (more are needed for real usage)
	- Valid post logout redirect URIs:
		- http://localhost:5173/endsession
		- http://localhost:5173/recipewatch/endsession
		- https://gaia.ispatial.survey.ntua.gr/recipewatch/endsession
		- **TODO** (more are needed for real usage)
	- Web origins:
		- http://localhost:5173
		- **TODO** (more are needed for real usage)
- Save & finish the new client wizard

### Create a client for Responsible Cooking Alliance

Create a new client in the Realm:

- General settings:
	- Client type: OpenID Connect
	- Client ID: rca
	- Name: Responsible Cooking Alliance
- Capability config:
	- Client authentication: Off
	- Authorization: Off
	- Authentication flow: Check *only* "Standard flow"
    - PKCE Method: **TODO** (should be S256 - also in the "Settings" tab, under the "Capability config" section)
- Login settings:
	- Root URL: **TODO**
	- Home URL: **TODO**
	- Valid redirect URIs:
		- http://localhost:5173
		- http://localhost:5173/
		- http://localhost:8180/extension-callback.html
		- **TODO**
	- Valid post logout redirect URIs:
		- **TODO**
	- Web origins:
		- http://localhost:5173
		- **TODO**
- Save & finish the new client wizard

### Create some test users

Go to Manage → Users in the left menu, press "Create new user"

- Email verified: On
- General
	- Email: bob@krusty-krab.com
	- First name: Bob
	- Last name: Squarepants
- Create
- Role mapping -> Assign Role -> Filter by realm roles (dropdown, top-left of the popup) -> `offline_access`

The ID is `716e038e-9f28-4f67-a797-2cb297818546`. Go to the "Credentials" tab, "Set password" to `bob`. Make sure
"Temporary" is Off and press "Save".

### Export the configuration

Exporting from the UI does not include users in the export file; you have to use the CLI:

```bash
docker exec -it dietwise-keycloak-1 bash
# in the container
/opt/keycloak/bin/kc.sh export --dir /opt/keycloak/data/import --users realm_file --realm dietwise
exit # return to host
# in the host
docker cp dietwise-keycloak-1:/opt/keycloak/data/import/dietwise-realm.json dietwise-docker/dietwise-docker-keycloak/src/main/docker/
```
