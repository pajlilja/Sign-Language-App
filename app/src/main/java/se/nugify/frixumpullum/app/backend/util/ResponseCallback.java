package se.nugify.frixumpullum.app.backend.util;

import android.os.Handler;

/**
 * Created by marcus on 2017-04-21.
 */

public class ResponseCallback {
    private Handler handler;

    public ResponseCallback() {
        handler = new Handler();
    }

    public Handler getHandler() {
        return handler;
    }

    public void onResponse(String response) {}
    public void onNoConnection() {}
}
