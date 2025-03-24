# DiGuRaL App

This subproject contains an implementation of the Cyface App used for the mFUND Research Project DiGuRaL.

## Building the Application

To build this application successfully, the correct properties must be set in gradle.properties at the root of this repository.
To upload captured data set `digural.staging_api` to an appropriate WebDAV server address.

Also there must be a file called `sentry.properties` with the proper [Sentry](https://sentry.io/welcome/) settings.
An example looks like:

```
defaults.project=android-app
defaults.org=cyface
auth.token=<auth.token>
```

You need to ensure you add a proper auth token from you own Sentry project yourselfs, of course.

## Implementation Details

The app uses the classic [Activity/Fragment Style](https://developer.android.com/develop/ui/views/layout/declaring-layout) way to implement a UI.

To manipulate the settings screen start by adding new User Interface Elements to `src/main/res/layout/fragment_settings.xml`.
Elements associated with the camera are hidden initially.

### Internationalization

The application is available in English and German.
To ensure proper translation please uses text shown to the user only from `strings.xml`.
The default file in English is located at `src/main/res/values/strings.xml`.
The German translation resides in `src/main/res/values-de/strings.xml`.