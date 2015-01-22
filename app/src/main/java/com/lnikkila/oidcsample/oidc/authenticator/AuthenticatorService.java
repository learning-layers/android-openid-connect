package com.lnikkila.oidcsample.oidc.authenticator;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * The service that lets Android know about the custom Authenticator.
 *
 * @author Leo Nikkilä
 */
public class AuthenticatorService extends Service {

    private final String TAG = getClass().getSimpleName();

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binding Authenticator.");

        Authenticator authenticator = new Authenticator(this);
        return authenticator.getIBinder();
    }

}
