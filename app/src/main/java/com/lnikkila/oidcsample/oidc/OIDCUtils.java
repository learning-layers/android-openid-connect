package com.lnikkila.oidcsample.oidc;

import android.text.TextUtils;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest;
import com.google.api.client.auth.oauth2.AuthorizationRequestUrl;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.RefreshTokenRequest;
import com.google.api.client.auth.oauth2.TokenRequest;
import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.auth.openidconnect.IdTokenResponse;
import com.google.api.client.auth.openidconnect.IdTokenVerifier;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A layer of syntactic sugar around the google-oauth-java-client library to simplify using OpenID
 * Access on Android.
 *
 * Currently this helper class is fairly limited. It's suitable for our use case and pretty much
 * nothing else. Pull requests are appreciated!
 *
 * @author Leo Nikkilä
 * @author Camilo Montes
 */
public class OIDCUtils {

    /**
     * Generates an URL to the Authorization Endpoint. The user can then authenticate themselves,
     * authorise this app and obtain an Authorization Token.
     * @deprecated use {@link OIDCUtils#codeFlowAuthenticationUrl} instead.
     */
    @SuppressWarnings("unused")
    @Deprecated
    public static String newAuthorizationUrl(String authorizationServerUrl, String tokenServerUrl,
                                             String redirectUrl, String clientId,
                                             String clientSecret, String[] scopes) {

        List<String> scopesList = Arrays.asList(scopes);

        AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(
                BearerToken.authorizationHeaderAccessMethod(),
                AndroidHttp.newCompatibleTransport(),
                new GsonFactory(),
                new GenericUrl(tokenServerUrl),
                new BasicAuthentication(clientId, clientSecret),
                clientId,
                authorizationServerUrl
        ).build();

        // Generate the URL that we'll modify a bit to comply with the spec
        AuthorizationCodeRequestUrl authUrl = flow.newAuthorizationUrl();

        // The flow builder sets `response_type` and `client_id` parameters. The OpenID spec
        // requires the `scope` and `redirect_uri` parameters as well.
        authUrl.setScopes(scopesList);
        authUrl.setRedirectUri(redirectUrl);

        // If the list of scopes includes the special `offline_access` scope that enables issuing
        // of Refresh Tokens, we need to ask for consent by including this parameter.
        if (scopesList.contains("offline_access")) {
            authUrl.set("prompt", "consent");
        }

        // Tell the server to ask for login details again. This ensures that in case of multiple
        // accounts, the user won't accidentally authorise the wrong one.
        authUrl.set("prompt", "login");

        // An optional request parameter that asks the server to provide a touch-enabled interface.
        // Who knows, maybe the server is nice enough to make some changes.
        authUrl.set("display", "touch");

        return authUrl.toString();
    }

    /**
     * Generates an Authentication Request URL to the Authorization Endpoint to start an Implicit Flow.
     * When using the Implicit Flow, all tokens are returned from the Authorization Endpoint; the
     * Token Endpoint is not used so it allows to get all tokens on one trip. The downside is that
     * it doesn't support refresh tokens.
     * @see <a href="http://openid.net/specs/openid-connect-core-1_0.html#ImplicitFlowAuth">Implicit Flow</a>
     */
    public static String implicitFlowAuthenticationUrl(String authorizationServerUrl, String clientId,
                                                       String redirectUrl, String[] scopes) {

        //TODO: see what the following statement implies :
        // "While OAuth 2.0 also defines the token Response Type value for the Implicit Flow,
        // OpenID Connect does not use this Response Type, since no ID Token would be returned"
        // from http://openid.net/specs/openid-connect-core-1_0.html#Authentication
        String[] responsesTypes = {"id_token", "token"};
        List<String> scopesList = Arrays.asList(scopes);
        List<String> responsesList = Arrays.asList(responsesTypes);

        //REQUIRED  OIDC request params
        AuthorizationRequestUrl request = new AuthorizationRequestUrl(authorizationServerUrl, clientId,
                responsesList)
                .setRedirectUri(redirectUrl)
                .setScopes(scopesList);

        //OPTIONAL OIDC request params
        if (scopesList.contains("offline_access")) {
            // If the list of scopes includes the special `offline_access` scope that enables issuing
            // of Refresh Tokens, we need to ask for consent by including this parameter.
            request.set("prompt", "consent");
        } else {
            // Tell the server to ask for login details again. This ensures that in case of multiple
            // accounts, the user won't accidentally authorise the wrong one.
            request.set("prompt", "login");
        }

        // An optional request parameter that asks the server to provide a touch-enabled interface.
        // Who knows, maybe the server is nice enough to make some changes.
        request.set("display", "touch");

        return request.build();
    }

    /**
     * Generates an Authentication Request URL to the Authorization Endpoint to start an Hybrid Flow.
     * When using the Hybrid Flow, some tokens are returned from the Authorization Endpoint and
     * others are returned from the Token Endpoint.
     * @see <a href="http://openid.net/specs/openid-connect-core-1_0.html#HybridFlowAuth">Hybrid Flow</a>
     */
    public static String hybridFlowAuthenticationUrl(String authorizationServerUrl, String clientId,
                                                     String redirectUrl, String[] scopes) {

        // The response type "code" is the only mandatory response type on hybrid flow, it must be
        // coupled with other response types to form one of the following values : "code id_token",
        // "code token", or "code id_token token".
        // For our needs "token" is not defined here because we want an access_token that has made
        // a client authentication. That access_token will be retrieve later using the TokenEndpoint
        // (see #requestTokens).
        String[] responsesTypes = {"code", "id_token"};
        List<String> scopesList = Arrays.asList(scopes);
        List<String> responsesList = Arrays.asList(responsesTypes);

        //REQUIRED  OIDC request params
        AuthorizationRequestUrl request = new AuthorizationRequestUrl(authorizationServerUrl, clientId, responsesList)
                .setRedirectUri(redirectUrl)
                .setScopes(scopesList);

        //OPTIONAL OIDC request params
        if (scopesList.contains("offline_access")) {
            // If the list of scopes includes the special `offline_access` scope that enables issuing
            // of Refresh Tokens, we need to ask for consent by including this parameter.
            request.set("prompt", "consent");
        } else {
            // Tell the server to ask for login details again. This ensures that in case of multiple
            // accounts, the user won't accidentally authorise the wrong one.
            request.set("prompt", "login");
        }

        // An optional request parameter that asks the server to provide a touch-enabled interface.
        // Who knows, maybe the server is nice enough to make some changes.
        request.set("display", "touch");

        return request.build();
    }

    /**
     * Generates an Authentication Request URL to the Authorization Endpoint to start an Code Flow.
     * When using the Code Flow, all tokens are returned from the Token Endpoint.
     * The Authorization Server can authenticate the Client before exchanging the Authorization Code
     * for an Access Token.
     * @see <a href="http://openid.net/specs/openid-connect-core-1_0.html#CodeFlowAuth">Code Flow</a>
     */
    public static String codeFlowAuthenticationUrl(String authorizationServerUrl, String clientId,
                                                   String redirectUrl, String[] scopes) {

        List<String> scopesList = Arrays.asList(scopes);

        AuthorizationCodeRequestUrl request = new AuthorizationCodeRequestUrl(authorizationServerUrl, clientId)
                .setRedirectUri(redirectUrl)
                .setScopes(scopesList);

        //OPTIONAL OIDC request params
        if (scopesList.contains("offline_access")) {
            // If the list of scopes includes the special `offline_access` scope that enables issuing
            // of Refresh Tokens, we need to ask for consent by including this parameter.
            request.set("prompt", "consent");
        } else {
            // Tell the server to ask for login details again. This ensures that in case of multiple
            // accounts, the user won't accidentally authorise the wrong one.
            request.set("prompt", "login");
        }

        // An optional request parameter that asks the server to provide a touch-enabled interface.
        // Who knows, maybe the server is nice enough to make some changes.
        request.set("display", "touch");

        return request.build();
    }

    /**
     * Exchanges an Authorization Token for an ID Token, Access Token and Refresh Token.
     *
     * Generally ID tokens are valid for a longer period of time than access tokens, since they're
     * meant for authentication. The way I (maybe) understand it, access tokens should be used for
     * short-term access to protected resources.
     *
     * Needs to be run on a separate thread.
     * @deprecated use {@link OIDCUtils#refreshTokens(String, String, String, String[], String)}
     * instead.
     */
    @SuppressWarnings("unused")
    @Deprecated
    public static IdTokenResponse requestTokens(String authorizationServerUrl,
                                                String tokenServerUrl, String redirectUrl,
                                                String clientId, String clientSecret,
                                                String authToken) throws IOException {

        AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(
                BearerToken.authorizationHeaderAccessMethod(),
                AndroidHttp.newCompatibleTransport(),
                new GsonFactory(),
                new GenericUrl(tokenServerUrl),
                new BasicAuthentication(clientId, clientSecret),
                clientId,
                authorizationServerUrl
        ).build();

        TokenRequest request = flow.newTokenRequest(authToken);

        // Again, we need to set the `redirect_uri` parameter. This time a dedicated method
        // setRedirectUri() doesn't exist for some reason.
        request.set("redirect_uri", redirectUrl);

        IdTokenResponse response = IdTokenResponse.execute(request);
        String idToken = response.getIdToken();

        if (isValidIdToken(clientId, idToken)) {
            return response;
        } else {
            throw new IOException("Invalid ID token returned.");
        }
    }

    /**
     * Exchanges an Authorization Code for an Access Token, Refresh Token and (optional) ID Token.
     * This provides the benefit of not exposing any tokens to the User Agent and possibly other
     * malicious applications with access to the User Agent.
     * The Authorization Server can also authenticate the Client before exchanging the Authorization
     * Code for an Access Token.
     *
     * Needs to be run on a separate thread.
     *
     * @throws IOException
     */
    public static IdTokenResponse requestTokens(String tokenServerUrl, String redirectUrl,
                                                String clientId, String clientSecret,
                                                String authCode) throws IOException {

        AuthorizationCodeTokenRequest request = new AuthorizationCodeTokenRequest(
                AndroidHttp.newCompatibleTransport(),
                new GsonFactory(),
                new GenericUrl(tokenServerUrl),
                authCode
        );
        request.set("redirect_uri", redirectUrl);

        if (!TextUtils.isEmpty(clientSecret)) {
            request.setClientAuthentication(new BasicAuthentication(clientId, clientSecret));
        }

        IdTokenResponse response = IdTokenResponse.execute(request);
        String idToken = response.getIdToken();

        if (isValidIdToken(clientId, idToken)) {
            return response;
        } else {
            throw new IOException("Invalid ID token returned.");
        }
    }

    /**
     * Exchanges a Refresh Token for a new set of tokens.
     *
     * Note that the Token Server may require you to use the `offline_access` scope to receive
     * Refresh Tokens.
     */
    public static IdTokenResponse refreshTokens(String tokenServerUrl, String clientId,
                                                String clientSecret, String[] scopes,
                                                String refreshToken) throws IOException {

        List<String> scopesList = Arrays.asList(scopes);

        RefreshTokenRequest request = new RefreshTokenRequest(
                AndroidHttp.newCompatibleTransport(),
                new GsonFactory(),
                new GenericUrl(tokenServerUrl),
                refreshToken
        );

        if (!TextUtils.isEmpty(clientSecret)) {
            request.setClientAuthentication(new BasicAuthentication(clientId, clientSecret));
        }
        request.setScopes(scopesList);

        return IdTokenResponse.execute(request);
    }

    /**
     * Verifies an ID Token.
     * TODO: Look into verifying the token issuer as well?
     */
    public static boolean isValidIdToken(String clientId, String tokenString) throws IOException {

        List<String> audiences = Arrays.asList(clientId);
        IdTokenVerifier verifier = new IdTokenVerifier.Builder().setAudience(audiences).build();

        IdToken idToken = IdToken.parse(new GsonFactory(), tokenString);

        return verifier.verify(idToken);
    }

    /**
     * Gets user information from the UserInfo endpoint.
     */
    public static Map getUserInfo(String userInfoUrl, String idToken) throws IOException {
        HttpRequest request = new HttpRequest(userInfoUrl, HttpRequest.METHOD_GET);
        request = prepareApiRequest(request, idToken);

        if (request.ok()) {
            String jsonString = request.body();
            return new Gson().fromJson(jsonString, Map.class);
        } else {
            throw new IOException(request.message());
        }
    }

    /**
     * Prepares an arbitrary API request by injecting an ID Token into an HttpRequest. Uses an
     * external library to make my life easier, but you can modify this to use whatever in case you
     * don't like the (small) dependency.
     */
    public static HttpRequest prepareApiRequest(HttpRequest request, String idToken)
            throws IOException {

        return request.authorization("Bearer " + idToken).acceptJson();
    }

}
