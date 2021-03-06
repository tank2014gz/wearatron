package com.thisisnotajoke.wearatron.controller;

import android.app.Dialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.widget.Toast;

import com.crittercism.app.Crittercism;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;
import com.thisisnotajoke.wearatron.GeofenceManager;
import com.thisisnotajoke.lockitron.Lock;
import com.thisisnotajoke.lockitron.controller.WearatronActivity;
import com.thisisnotajoke.lockitron.model.DataManager;
import com.thisisnotajoke.wearatron.BuildConfig;
import com.thisisnotajoke.wearatron.R;

import javax.inject.Inject;

public class MainActivity extends WearatronActivity implements LockListFragment.Callbacks, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {
    private static final String STATE_RESOLVING_ERROR = "resolving_error";
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    private static final String DIALOG_ERROR = "dialog_error";
    private static final String TAG = "MainActivity";

    private String mToken;

    private GoogleApiClient mGoogleApiClient;
    private boolean mResolvingError;
    private Lock mLock;

    @Inject
    DataManager mDataManager;

    @Inject
    GeofenceManager mGeofenceManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(!BuildConfig.DEBUG) {
            Crittercism.initialize(getApplicationContext(), "5457ff21bb9475497d000001");
        }
        mToken = mDataManager.getToken().getToken();
        mLock = mDataManager.getActiveLock();
        setContentView(getLayoutResId());
        FragmentManager manager = getSupportFragmentManager();
        Fragment fragment = createFragment();
        manager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();

        mResolvingError = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_RESOLVING_ERROR, false);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {  // more about this later
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        if(mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_RESOLVING_ERROR, mResolvingError);
    }

    private Fragment createFragment() {
        return LockListFragment.newInstance(mToken, mLock);
    }

    private int getLayoutResId() {
        return R.layout.activity_fragment;
    }

    @Override
    public void onLockSelected(final Lock lock) {
        mLock = lock;
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, new Intent(this, ReceiveTransitionsIntentService.class), PendingIntent.FLAG_UPDATE_CURRENT);

        new AsyncTask<PendingIntent, Void, Void>() {
            @Override
            protected Void doInBackground(PendingIntent... params) {
                mDataManager.setActiveLock(lock);
                mGeofenceManager.setFenceLocation();
                mGeofenceManager.registerGeofences(params[0]);

                return null;
            }
        }.execute(pendingIntent);
        stopService(new Intent(this, MobileListenerService.class));


        Toast.makeText(this, R.string.lock_selected, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_RESOLVE_ERROR) {
            mResolvingError = false;
            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!mGoogleApiClient.isConnecting() &&
                        !mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.connect();
                }
            }
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
            if (mResolvingError) {
                // Already attempting to resolve an error.
                return;
            } else if (result.hasResolution()) {
                try {
                    mResolvingError = true;
                    result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
                } catch (IntentSender.SendIntentException e) {
                    // There was an error with the resolution intent. Try again.
                    mGoogleApiClient.connect();
                }
            } else {
                // Show dialog using GooglePlayServicesUtil.getErrorDialog()
                showErrorDialog(result.getErrorCode());
                mResolvingError = true;
            }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "GAC connected");
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    /* Creates a dialog for an error message */
    private void showErrorDialog(int errorCode) {
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(getSupportFragmentManager(), "errordialog");
    }

    /* Called from ErrorDialogFragment when the dialog is dismissed. */
    public void onDialogDismissed() {
        mResolvingError = false;
    }

    /* A fragment to display an error dialog */
    public static class ErrorDialogFragment extends DialogFragment {
        public ErrorDialogFragment() { }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GooglePlayServicesUtil.getErrorDialog(errorCode,
                    this.getActivity(), REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            ((MainActivity)getActivity()).onDialogDismissed();
        }
    }

    @Override
    protected boolean usesInjection() {
        return true;
    }
}
