package com.lnikkila.oidcsample;

import android.accounts.AccountManager;
import android.content.Context;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.gson.Gson;
import com.lnikkila.oidcsample.oidc.OIDCUtils;

import java.io.IOException;
import java.util.Map;

/**
 * An incomplete class that illustrates how to make API requests with the ID Token.
 *
 * @author Leo Nikkilä
 */
public class APIUtility {

    private final String TAG = this.getClass().getSimpleName();

    public static final int HTTP_UNAUTHORIZED = 401;
    public static final int HTTP_FORBIDDEN = 403;

    /**
     * Makes a GET request and parses the received JSON string as a Map.
     */
    public static Map getJson(Context context, String url, String idToken)
            throws IOException {

        String jsonString = makeRequest(context, HttpRequest.METHOD_GET, url, idToken);
        return new Gson().fromJson(jsonString, Map.class);
    }

    /**
     * Makes an arbitrary HTTP request.
     *
     * If the request doesn't execute successfully on the first try, the tokens will be refreshed
     * and the request will be retried. If the second try fails, an exception will be raised.
     */
    public static String makeRequest(Context context, String method, String url, String idToken)
            throws IOException {

        return makeRequest(context, method, url, idToken, true);
    }

    private static String makeRequest(Context context, String method, String url, String idToken,
                                     boolean doRetry) throws IOException {

        AccountManager accountManager = AccountManager.get(context);

        HttpRequest request = new HttpRequest(url, method);
        request = OIDCUtils.prepareApiRequest(request, idToken);

        if (request.ok()) {
            return request.body();
        } else {
            int code = request.code();

            if (doRetry && (code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN)) {
                // We're being denied access on the first try, let's renew the token and retry
                accountManager.invalidateAuthToken(context.getString(R.string.ACCOUNT_TYPE), idToken);
                return makeRequest(context, method, url, idToken, false);
            } else {
                // An unrecoverable error or the renewed token didn't work either
                throw new IOException(request.code() + " " + request.message());
            }
        }
    }

}
