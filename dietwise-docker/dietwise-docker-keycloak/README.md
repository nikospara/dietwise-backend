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
	- Sessions: Make sure "Offline session settings" -> "Offline Session Idle" is set to something like 30 Days
	- User profile:
		- Row `firstName` -> Edit -> "Required field": No -> Save
		- Row `lastName`, the same
- Realm roles:
	- Create Role: "influencer"
- **FOR PRODUCTION:** Create user "master", no groups, press "Create", tab "Role mapping", "Assign role", "Realm roles", "admin"

### Create a client for RecipeWatch

Create a new client in the Realm:

- General settings:
	- Client type: OpenID Connect
	- Client ID: recipewatch
	- Name: MyRecipeWatch
- Capability config:
	- Client authentication: Off
	- Authorization: Off
	- Authentication flow: Check *only* "Standard flow"
	- Require PKCE: On
	- PKCE Method: S256
- Login settings:
	- Root URL: (empty)
	- Home URL: (empty)
	- Valid redirect URIs:
		- http://localhost:5173/authcallback
		- http://localhost:5173/recipewatch/authcallback
		- https://gaia.ispatial.survey.ntua.gr/recipewatch/authcallback
		- https://gaia.ispatial.survey.ntua.local/recipewatch/authcallback
		- eu.dietwise.recipewatch://authcallback
		- **FOR PRODUCTION:**
			- eu.dietwise.recipewatch://authcallback
			- https://dietwise.ispatial.survey.ntua.gr/recipewatch/authcallback
	- Valid post logout redirect URIs:
		- http://localhost:5173/endsession
		- http://localhost:5173/recipewatch/endsession
		- https://gaia.ispatial.survey.ntua.gr/recipewatch/endsession
		- https://gaia.ispatial.survey.ntua.local/recipewatch/endsession
		- eu.dietwise.recipewatch://endsession
		- **FOR PRODUCTION:**
			- eu.dietwise.recipewatch://endsession
			- https://dietwise.ispatial.survey.ntua.gr/recipewatch/endsession
	- Web origins:
		- http://localhost:5173
        - https://localhost (for the native Ionic deployment)
		- **FOR PRODUCTION:**
			- https://localhost (for the native Ionic deployment)
			- https://dietwise.ispatial.survey.ntua.gr
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
	- Require PKCE: On
	- PKCE Method: S256
- Login settings:
	- Root URL: (empty)
	- Home URL: (empty)
	- Valid redirect URIs:
		- http://localhost:5173
		- http://localhost:5173/
		- http://localhost:8180/extension-callback.html
		- https://e9daffa4e57cef0785f2756a14b247b128162e80.extensions.allizom.org/
		- https://gaia.ispatial.survey.ntua.gr/extension-callback.html
		- https://gaia.ispatial.survey.ntua.local/extension-callback.html
		- **FOR PRODUCTION:** (not magic, see [here](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/identity#getting_the_redirect_url), run `node -e "console.log(require('crypto').createHash('sha1').update('responsible-cooking-alliance@dietwise.eu').digest('hex'))"`)
			- https://dietwise.ispatial.survey.ntua.gr/extension-callback.html
			- https://e9daffa4e57cef0785f2756a14b247b128162e80.extensions.allizom.org/
	- Valid post logout redirect URIs:
		- (empty)
	- Web origins:
		- (empty)
- Save & finish the new client wizard

### Create two clients for the account deletion

The public client:

- General settings:
	- Client type: OpenID Connect
	- Client ID: account-deletion
	- Name: Account deletion
- Capability config:
	- Client authentication: Off
	- Authorization: Off
	- Authentication flow: Check *only* "Standard flow"
	- Require PKCE: On
	- PKCE Method: S256
- Login settings:
	- Root URL: (empty)
	- Home URL: (empty)
	- Valid redirect URIs:
		- http://localhost:8180/dietwise-account-deletion.html
		- https://gaia.ispatial.survey.ntua.gr/dietwise-account-deletion.html
		- **FOR PRODUCTION:**
			- https://dietwise.ispatial.survey.ntua.gr/dietwise-account-deletion.html
	- Valid post logout redirect URIs:
		- (empty)
	- Web origins:
		- http://localhost:8180
		- **FOR PRODUCTION:**
			- https://dietwise.ispatial.survey.ntua.gr
- Save & finish the new client wizard

The confidential service-account client:

- General settings:
	- Client type: OpenID Connect
	- Client ID: backend-account
	- Name: Backend account
- Capability config:
	- Client authentication: On
	- Authorization: Off
	- Authentication flow: Check *only* "Service accounts roles"
	- Require PKCE: On
	- PKCE Method: S256
- Login settings (leave blank, as long as this is not intended to be used by people)
- Save & finish the new client wizard
- Go to Service account roles tab, Assign role -> Client roles -> `manage-users` This appears as "(realm-management) manage-users" in the UI
- Go to the Credentials tab, define the "Client Secret"

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
