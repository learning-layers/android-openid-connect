
OpenID Connect Sample for Android
=================================

![The app icon with the OpenID logo.](app/src/main/res/drawable-xxhdpi/ic_launcher.png)

**An Android app that communicates with a non-Google OpenID Connect provider.**

Use [Google’s own APIs][1], if you want to connect to their OpenID provider servers. This
project is meant to connect to non-Google providers, which those APIs don’t support.

Since OpenID Connect is basically an extension of OAuth 2, it’s convenient to use readily
available libraries as the foundation. This is built upon [google-oauth-java-client][2].

Features
--------

- integration with Android’s `AccountManager`
- support for multiple accounts
- login/authorisation via a WebView
- refreshing tokens when needed
- requesting user information
- making authenticated API calls
- heavily commented code

Usage
-----

**You’ll need to register your app with an OIDC provider and put your configuration data into
[`Config.java`][3].**

When you launch the app, you’ll see this:

[![The app with a button prompting the user to log in.](https://i.imgur.com/sz4ArDU.png)](https://i.imgur.com/TTo5AkD.png)

Tapping the button will let you log in to the provider and authorise the app to use your data.

[![An Android WebView displaying a provider’s authorisation form.](https://i.imgur.com/2JNiHGK.png)](https://i.imgur.com/F2GkOQL.png)

If all goes well:

1. the app gets authorisation
2. the tokens are saved and associated with an account using Android’s `AccountManager`
3. the button will indicate that you’ve logged in by displaying your username. (Assuming that the
   provider has set `preferred_username`.)

You can add more accounts via Android’s settings. When there are multiple accounts, the app will
ask you to choose one of them when logging in.

Dependencies
------------

This project depends on the following libraries. They are fetched automatically via Maven. The last
three are for convenience and can probably be written out if needed.

- [google-oauth-java-client][2]
- google-api-client-gson
- google-api-client-android
- [http-request][4]

History
-------

This project was originally made to be included in the [Ach So!][5] Learning Layers project.

It was developed by [Leo Nikkilä][6] at the Learning Environments research group of Aalto
University, Finland.

Legalese
--------

Licensed under the MIT licence. See [LICENSING.md][7].


[1]: https://developers.google.com/accounts/docs/OAuth2Login
[2]: https://code.google.com/p/google-oauth-java-client/
[3]: /app/src/main/java/com/lnikkila/oidcsample/Config.java
[4]: https://github.com/kevinsawicki/http-request
[5]: https://github.com/learning-layers/AchSo
[6]: https://github.com/lnikkila
[7]: LICENSING.md
