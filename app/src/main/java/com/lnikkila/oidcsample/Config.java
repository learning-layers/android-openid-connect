package com.lnikkila.oidcsample;

public class Config {

    // TODO: Add the information you received from your OIDC provider here.

    public static final String clientId = "foobar";
    public static final String clientSecret = "xyzzy";

    public static final String authorizationServerUrl = "https://www.example.com/oauth2/authorize";
    public static final String tokenServerUrl = "https://www.example.com/oauth2/token";
    public static final String userInfoUrl = "https://www.example.com/oauth2/userinfo";

    // This URL doesn't really have a use with native apps and basically just signifies the end
    // of the authorisation process. It doesn't have to be a real URL, but it does have to be the
    // same URL that is registered with your provider.
    public static final String redirectUrl = "app://oidcsample.lnikkila.com";

    // The `offline_access` scope enables us to request Refresh Tokens, so we don't have to ask the
    // user to authorise us again every time the tokens expire. Some providers might have an
    // `offline` scope instead. If you get an `invalid_scope` error when trying to authorise the
    // app, try changing it to `offline`.
    public static final String[] scopes = {"openid", "profile", "offline_access"};

}
