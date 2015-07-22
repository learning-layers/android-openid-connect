package com.lnikkila.oidcsample.oidc.authenticator;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.auth.openidconnect.IdTokenResponse;
import com.lnikkila.oidcsample.Config;
import com.lnikkila.oidcsample.oidc.OIDCUtils;

import java.io.IOException;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;

/**
 * Used by Android's AccountManager to manage our account information.
 *
 * The three OpenID tokens (not counting the single-use Authorization Token that is discarded) are
 * stored as what Android calls "auth tokens". They all have different token types:
 *
 * ID Token:      TOKEN_TYPE_ID
 * Access Token:  TOKEN_TYPE_ACCESS  (replaceable by the ID Token, so we're not really using this)
 * Refresh Token: TOKEN_TYPE_REFRESH
 *
 * @author Leo Nikkilä
 */
public class Authenticator extends AbstractAccountAuthenticator {

    private final String TAG = getClass().getSimpleName();

    private Context context;
    private AccountManager accountManager;

    public static final String TOKEN_TYPE_ID = "com.lnikkila.oidcsample.TOKEN_TYPE_ID";
    public static final String TOKEN_TYPE_ACCESS = "com.lnikkila.oidcsample.TOKEN_TYPE_ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "com.lnikkila.oidcsample.TOKEN_TYPE_REFRESH";

    public Authenticator(Context context) {
        super(context);
        this.context = context;

        accountManager = AccountManager.get(context);

        Log.d(TAG, "Authenticator created.");
    }

    /**
     * Called when the user adds a new account through Android's system settings or when an app
     * explicitly calls this.
     */
    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType,
                             String authTokenType, String[] requiredFeatures, Bundle options) {

        Log.d(TAG, String.format("addAccount called with accountType %s, authTokenType %s.",
                accountType, authTokenType));

        Bundle result = new Bundle();

        Intent intent = createIntentForAuthorization(response);

        // We're creating a new account, not just renewing our authorisation
        intent.putExtra(AuthenticatorActivity.KEY_IS_NEW_ACCOUNT, true);

        result.putParcelable(AccountManager.KEY_INTENT, intent);

        return result;
    }

    /**
     * Tries to retrieve a previously stored token of any type. If the token doesn't exist yet or
     * has been invalidated, we need to request a set of replacement tokens.
     */
    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account,
                               String authTokenType, Bundle options) {

        Log.d(TAG, String.format("getAuthToken called with account.type '%s', account.name '%s', " +
                "authTokenType '%s'.", account.type, account.name, authTokenType));

        // Try to retrieve a stored token
        String token = accountManager.peekAuthToken(account, authTokenType);

        if (TextUtils.isEmpty(token)) {
            // If we don't have one or the token has been invalidated, we need to check if we have
            // a refresh token
            Log.d(TAG, "Token empty, checking for refresh token.");
            String refreshToken = accountManager.peekAuthToken(account, TOKEN_TYPE_REFRESH);

            if (TextUtils.isEmpty(refreshToken)) {
                // If we don't even have a refresh token, we need to launch an intent for the user
                // to get us a new set of tokens by authorising us again.

                Log.d(TAG, "Refresh token empty, launching intent for renewing authorisation.");

                Bundle result = new Bundle();
                Intent intent = createIntentForAuthorization(response);

                // Provide the account that we need re-authorised
                intent.putExtra(AuthenticatorActivity.KEY_ACCOUNT_OBJECT, account);

                result.putParcelable(AccountManager.KEY_INTENT, intent);
                return result;
            } else {
                // Got a refresh token, let's use it to get a fresh set of tokens
                Log.d(TAG, "Got refresh token, getting new tokens.");

                IdTokenResponse tokenResponse;

                try {
                    tokenResponse = OIDCUtils.refreshTokens(Config.tokenServerUrl,
                                                            Config.clientId,
                                                            Config.clientSecret,
                                                            Config.scopes,
                                                            refreshToken);

                    Log.d(TAG, "Got new tokens.");

                    accountManager.setAuthToken(account, TOKEN_TYPE_ID, tokenResponse.getIdToken());
                    accountManager.setAuthToken(account, TOKEN_TYPE_ACCESS, tokenResponse.getAccessToken());
                    accountManager.setAuthToken(account, TOKEN_TYPE_REFRESH, tokenResponse.getRefreshToken());
                }catch (TokenResponseException e) {
                    if(e.getStatusCode() == HTTP_BAD_REQUEST && e.getContent().contains("invalid_grant")) {
                        // If the refresh token has expired, we need to launch an intent for the user
                        // to get us a new set of tokens by authorising us again.

                        Log.d(TAG, "Refresh token expired, launching intent for renewing authorisation.");

                        Bundle result = new Bundle();
                        Intent intent = createIntentForAuthorization(response);

                        // Provide the account that we need re-authorised
                        intent.putExtra(AuthenticatorActivity.KEY_ACCOUNT_OBJECT, account);

                        result.putParcelable(AccountManager.KEY_INTENT, intent);
                        return result;
                    }
                    else {
                        // There's not much we can do if we get here
                        Log.e(TAG, "Couldn't get new tokens.", e);
                    }
                }
                catch (IOException e) {
                    // There's not much we can do if we get here
                    Log.e(TAG, "Couldn't get new tokens.", e);

                }

                // Now, let's return the token that was requested
                token = accountManager.peekAuthToken(account, authTokenType);
            }
        }

        Log.d(TAG, String.format("Returning token '%s' of type '%s'.", token, authTokenType));

        Bundle result = new Bundle();

        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        result.putString(AccountManager.KEY_AUTHTOKEN, token);

        return result;
    }

    /**
     * Create an intent for showing the authorisation web page.
     */
    private Intent createIntentForAuthorization(AccountAuthenticatorResponse response) {
        Intent intent = new Intent(context, AuthenticatorActivity.class);

        // Generate a new authorisation URL
        String authUrl;

        switch (Config.flowType) {
            case AuthorizationCode :
                authUrl = OIDCUtils.codeFlowAuthenticationUrl(Config.authorizationServerUrl,
                        Config.clientId, Config.redirectUrl, Config.scopes);
                break;
            case Implicit:
                authUrl = OIDCUtils.implicitFlowAuthenticationUrl(Config.authorizationServerUrl,
                        Config.clientId, Config.redirectUrl, Config.scopes);
                break;
            case Hybrid:
                authUrl = OIDCUtils.hybridFlowAuthenticationUrl(Config.authorizationServerUrl,
                        Config.clientId, Config.redirectUrl, Config.scopes);
                break;
            default:
                Log.d(TAG, "Requesting unsupported flowType! Using CodeFlow instead");
                authUrl = OIDCUtils.codeFlowAuthenticationUrl(Config.authorizationServerUrl,
                        Config.clientId, Config.redirectUrl, Config.scopes);
                break;
        }

        Log.d(TAG, String.format("Created new intent with authorisation URL '%s'.", authUrl));

        intent.putExtra(AuthenticatorActivity.KEY_AUTH_URL, authUrl);

        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        return intent;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        return null;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account,
                              String[] features) throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        return null;
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account,
                                     Bundle options) throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
                                    String authTokenType, Bundle options)
                                    throws NetworkErrorException {
        return null;
    }

}
