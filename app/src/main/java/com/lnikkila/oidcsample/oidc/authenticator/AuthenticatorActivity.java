package com.lnikkila.oidcsample.oidc.authenticator;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.api.client.auth.openidconnect.IdTokenResponse;
import com.lnikkila.oidcsample.Config;
import com.lnikkila.oidcsample.oidc.OIDCUtils;
import com.lnikkila.oidcsample.R;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * An Activity that is launched by the Authenticator for requesting authorisation from the user and
 * creating an Account.
 *
 * The user will interact with the OIDC server via a WebView that monitors the URL for parameters
 * that indicate either a successful authorisation or an error. These parameters are set by the
 * spec.
 *
 * After the Authorization Token has successfully been obtained, we use the single-use token to
 * fetch an ID Token, an Access Token and a Refresh Token. We create an Account and persist these
 * tokens.
 *
 * @author Leo Nikkilä
 */
public class AuthenticatorActivity extends AccountAuthenticatorActivity {

    private final String TAG = getClass().getSimpleName();

    public static final String KEY_AUTH_URL = "com.lnikkila.oidcsample.KEY_AUTH_URL";
    public static final String KEY_IS_NEW_ACCOUNT = "com.lnikkila.oidcsample.KEY_IS_NEW_ACCOUNT";
    public static final String KEY_ACCOUNT_OBJECT = "com.lnikkila.oidcsample.KEY_ACCOUNT_OBJECT";

    private AccountManager accountManager;
    private Account account;
    private boolean isNewAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);

        accountManager = AccountManager.get(this);

        Bundle extras = getIntent().getExtras();

        // Are we supposed to create a new account or renew the authorisation of an old one?
        isNewAccount = extras.getBoolean(KEY_IS_NEW_ACCOUNT, false);

        // In case we're renewing authorisation, we also got an Account object that we're supposed
        // to work with.
        account = extras.getParcelable(KEY_ACCOUNT_OBJECT);

        // Fetch the authentication URL that was given to us by the calling activity
        String authUrl = extras.getString(KEY_AUTH_URL);

        Log.d(TAG, String.format("Initiated activity for getting authorisation with URL '%s'.",
                authUrl));

        // Initialise the WebView
        WebView webView = (WebView) findViewById(R.id.WebView);

        // TODO: Enable this if your authorisation page requires JavaScript
        // webView.getSettings().setJavaScriptEnabled(true);

        webView.loadUrl(authUrl);

        webView.setWebViewClient(new AuthorizationWebViewClient());
    }

    /**
     * Handles the result embedded in the redirect URI.
     *
     * @param redirectUriString Received redirect URI with query parameters.
     */
    private void finishAuthorization(String redirectUriString) {
        Uri redirectUri = Uri.parse(redirectUriString);
        Set<String> parameterNames = redirectUri.getQueryParameterNames();

        // The URL will contain a `code` parameter when the user has been authenticated
        if (parameterNames.contains("code")) {
            String authToken = redirectUri.getQueryParameter("code");

            // Request the ID token
            new RequestIdTokenTask().execute(authToken);
            return;
        }

        if (parameterNames.contains("error")) {
            // In case of an error, the `error` parameter contains an ASCII identifier, e.g.
            // "temporarily_unavailable" and the `error_description` *may* contain a
            // human-readable description of the error.
            //
            // For a list of the error identifiers, see
            // http://tools.ietf.org/html/rfc6749#section-4.1.2.1
            String error = redirectUri.getQueryParameter("error");
            String errorDescription = redirectUri.getQueryParameter("error_description");

            // If the user declines to authorise the app, there's no need to show an error message.
            if (error.equals("access_denied")) {
                return;
            }

            showErrorDialog("Error code: %s\n\n%s", error, errorDescription);
        }
    }

    /**
     * Tries to handle the given URI as the redirect URI.
     *
     * @param uri URI to handle.
     * @return Whether the URI was handled.
     */
    private boolean handleUri(String uri) {
        if (uri.startsWith(Config.redirectUrl)) {
            finishAuthorization(uri);
            return true;
        }

        return false;
    }

    private class AuthorizationWebViewClient extends WebViewClient {

        /**
         * Forces the WebView to not load the URL if it can be handled.
         */
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUri(url) || super.shouldOverrideUrlLoading(view, url);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String url) {
            showErrorDialog("Network error: got %s for %s.", description, url);
        }

    }

    /**
     * Requests the ID Token asynchronously.
     */
    private class RequestIdTokenTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... args) {
            String authToken = args[0];
            IdTokenResponse response;

            Log.d(TAG, "Requesting ID token.");

            try {
                response = OIDCUtils.requestTokens(Config.authorizationServerUrl,
                                                   Config.tokenServerUrl,
                                                   Config.redirectUrl,
                                                   Config.clientId,
                                                   Config.clientSecret,
                                                   authToken);
            } catch (IOException e) {
                Log.e(TAG, "Could not get response.");
                e.printStackTrace();
                return false;
            }

            if (isNewAccount) {
                createAccount(response);
            } else {
                setTokens(response);
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean wasSuccess) {
            if (wasSuccess) {
                // The account manager still wants the following information back
                Intent intent = new Intent();

                intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, account.name);
                intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, account.type);

                setAccountAuthenticatorResult(intent.getExtras());
                setResult(RESULT_OK, intent);
                finish();
            } else {
                showErrorDialog("Could not get ID Token.");
            }
        }
    }

    private void createAccount(IdTokenResponse response) {
        Log.d(TAG, "Creating account.");

        String accountType = getString(R.string.ACCOUNT_TYPE);

        // AccountManager expects that each account has a unique username. If a new account has the
        // same username as a previously created one, it will overwrite the older account.
        //
        // Unfortunately the OIDC spec cannot guarantee[1] that any user information is unique,
        // save for the user ID (i.e. the ID Token subject) which is hardly human-readable. This
        // makes choosing between multiple accounts difficult.
        //
        // We'll resort to naming each account `preferred_username (ID)`. This is a neat solution
        // if the user ID is short enough.
        //
        // [1]: http://openid.net/specs/openid-connect-basic-1_0.html#ClaimStability

        // Use the app name as a fallback if the other information isn't available for some reason.
        String accountName = getString(R.string.app_name);
        String accountId = null;

        try {
            accountId = response.parseIdToken().getPayload().getSubject();
        } catch (IOException e) {
            Log.e(TAG, "Could not get ID Token subject.");
            e.printStackTrace();
        }

        // Get the user information so we can grab the `preferred_username`
        Map userInfo = Collections.emptyMap();

        try {
            userInfo = OIDCUtils.getUserInfo(Config.userInfoUrl, response.getIdToken());
        } catch (IOException e) {
            Log.e(TAG, "Could not get UserInfo.");
            e.printStackTrace();
        }

        if (userInfo.containsKey("preferred_username")) {
            accountName = (String) userInfo.get("preferred_username");
        }

        account = new Account(String.format("%s (%s)", accountName, accountId), accountType);
        accountManager.addAccountExplicitly(account, null, null);

        // Store the tokens in the account
        setTokens(response);

        Log.d(TAG, "Account created.");
    }

    private void setTokens(IdTokenResponse response) {
        accountManager.setAuthToken(account, Authenticator.TOKEN_TYPE_ID, response.getIdToken());
        accountManager.setAuthToken(account, Authenticator.TOKEN_TYPE_ACCESS, response.getAccessToken());
        accountManager.setAuthToken(account, Authenticator.TOKEN_TYPE_REFRESH, response.getRefreshToken());
    }

    /**
     * TODO: Improve error messages.
     *
     * @param message Error message that can contain formatting placeholders.
     * @param args    Formatting arguments for the message, or null.
     */
    private void showErrorDialog(String message, String... args) {
        if (args != null) {
            message = String.format(message, args);
        }

        new AlertDialog.Builder(AuthenticatorActivity.this)
                .setTitle("Sorry, there was an error")
                .setMessage(message)
                .setCancelable(true)
                .setNeutralButton("Close", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        finish();
                    }
                })
                .create()
                .show();
    }

}
