package com.kelsonprime.lockitron.glass;

import android.content.Context;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.glass.media.Sounds;
import com.kelsonprime.lockitron.Lockitron;

import org.json.JSONException;
import org.json.JSONObject;

public class CommandTask extends AsyncTask<String, Void, Void> {
    private static final String TAG = "CommandTask";
    public static final String UNLOCK = "unlock";
    public static final String LOCK = "lock";
    private static final int SOCKET_TIMEOUT = 10000;

    private final AudioManager audio;
    private final RequestQueue queue;
    private final Context context;
    private final String lockUUID;
    private final String token;

    public CommandTask(Context context, String token, String lockUUID){
        super();
        this.context = context;
        this.token = token;
        this.lockUUID = lockUUID;
        queue = Volley.newRequestQueue(context);
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    private JsonObjectRequest buildRequest(final String command){
        //curl -i -F "access_token=STUFF" https://api.lockitron.com/v1/locks/UUID/unlock
        String url = Lockitron.ENDPOINT + "locks/"+lockUUID+"/"+command;

        JsonObjectRequest jsObjRequest = new JsonObjectRequest(Request.Method.POST, url, getParams(), new Response.Listener<JSONObject>() {

            @Override
            public void onResponse(JSONObject response) {
                Log.d(TAG, command + " success");
                Toast.makeText(context, R.string.success, Toast.LENGTH_SHORT).show();
                audio.playSoundEffect(Sounds.SUCCESS);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Error: " + error.getMessage());
                Log.e(TAG, "Error: " + error.getStackTrace().toString());
                NetworkResponse response = error.networkResponse;

                if (response != null) {
                    Log.e(TAG, "Network Response: " + response.toString());
                    switch (response.statusCode){
                        case 401:
                            Toast.makeText(context, R.string.authentication_error, Toast.LENGTH_SHORT).show();
                    }
                }
                audio.playSoundEffect(Sounds.ERROR);
            }
        });
        jsObjRequest.setRetryPolicy(new DefaultRetryPolicy(SOCKET_TIMEOUT, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        jsObjRequest.setShouldCache(false);
        Log.v(TAG, url);
        return jsObjRequest;
    }

    protected JSONObject getParams() {
        JSONObject params = new JSONObject();
        try {
            params.put("access_token", token);
        } catch (JSONException e) {
            Log.e(TAG, "JSOnException: " + e.getMessage());
        }
        return params;
    };

    @Override
    protected Void doInBackground(String... commands) {
        int count = commands.length;
        for (int i = 0; i < count; i++) {
            queue.add(buildRequest(commands[i]));
            // Escape early if cancel() is called
            if (isCancelled()) break;
        }
        return null;
    }
}
