package com.sungbin.kurento_android;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.sungbin.kurento_android.kurento.LocalParticipant;
import com.sungbin.kurento_android.kurento.RemoteParticipant;
import com.sungbin.kurento_android.kurento.Session;
import com.sungbin.kurento_android.permissions.PermissionsDialogFragment;
import com.sungbin.kurento_android.websocket.KurentoWebsocket;
import org.webrtc.EglBase;
import org.webrtc.MediaStream;
import org.webrtc.RendererCommon;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity  {
    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 100;
    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 101;
    private static final int MY_PERMISSIONS_REQUEST = 102;
    private final String TAG = MainActivity.class.getSimpleName();
    private static final int CAPTURE_PERMISSION_REQUEST_CODE = 1;
    LinearLayout views_container;
    Button start_finish_call;
    EditText kurento_url;
    EditText manager_edit;
    EditText worker_edit;
    SurfaceViewRenderer localVideoView;
    SurfaceViewRenderer remoteVideoView;
    TextView manager_participant;

    FrameLayout peer_container;
    Button switch_camera_btn;
    Button call_btn;

    private String KURENTO_URL;
    private String MANAGER_NAME;
    private String WORKER_NAME;

    private Session session;

    KurentoWebsocket webSocket;

    Toast toastMessage;
    Intent captureIntent;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_main);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        findViewById();
        askForPermissions();
        captureIntent();

    }
    private void findViewById(){
        views_container = findViewById(R.id.views_container);
        start_finish_call = findViewById(R.id.start_finish_call);
        kurento_url = findViewById(R.id.kurento_url);
        localVideoView = findViewById(R.id.local_gl_surface_view);
        peer_container = findViewById(R.id.peer_container);
        switch_camera_btn = findViewById(R.id.switch_camera_btn);
        manager_edit = findViewById(R.id.manager_edit);
        worker_edit = findViewById(R.id.worker_edit);
        remoteVideoView = findViewById(R.id.remote_gl_surface_view);
        manager_participant = findViewById(R.id.manager_participant);
        call_btn = findViewById(R.id.call_btn);
    }


    private void captureIntent(){
        if(Build.VERSION.SDK_INT < 21){
            Log.d(TAG,  "Your phone does not support this feature.");
            return;
        }
        MediaProjectionManager manager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        if(manager == null){
            Log.d(TAG,  "screenshots service unavailable");
            return;
        }
        Intent intent = manager.createScreenCaptureIntent();
        startActivityForResult(intent,CAPTURE_PERMISSION_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAPTURE_PERMISSION_REQUEST_CODE && resultCode == RESULT_OK) {
            captureIntent = data; // save data here
        }
    }

    public void buttonPressed(View view){
        if (start_finish_call.getText().equals(getResources().getString(R.string.hang_up))) {
            // Already connected to a session
            leaveSession(false);
            return;
        }
        if (arePermissionGranted()) {
            KURENTO_URL = kurento_url.getText().toString();
            MANAGER_NAME = manager_edit.getText().toString();
            WORKER_NAME = worker_edit.getText().toString();
            initViews();
            viewToConnectingState();

            join(captureIntent);

        } else {
            DialogFragment permissionsFragment = new PermissionsDialogFragment();
            permissionsFragment.show(getSupportFragmentManager(), "Permissions Fragment");
        }
    }

    private void join(Intent captureIntent){
        session = new Session(views_container, this);

        LocalParticipant localParticipant = new LocalParticipant(session, this.getApplicationContext(), localVideoView);
        localParticipant.startCamera(captureIntent);

        // Initialize and connect the websocket to kurento Server
        connectWebsocket();
    }

    private void connectWebsocket(){
        webSocket = new KurentoWebsocket(session, KURENTO_URL, this, MANAGER_NAME, WORKER_NAME);
        webSocket.execute();
        session.setWebSocket(webSocket);
    }

    private void initViews() {
        EglBase rootEglBase = EglBase.create();
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        localVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        localVideoView.setEnableHardwareScaler(true);
        localVideoView.setMirror(false);
    }
    public void leaveSession(boolean managerCall) {
        try {
            this.session.leaveSession(managerCall);
            viewToDisconnectedState();
        } catch (Exception ignore) {
        }
    }

    public void switchCamera(View view){
        if(session != null){
            this.session.switchCamera();
        }
    }

    public void viewToDisconnectedState() {
        runOnUiThread(() -> {
            localVideoView.clearImage();
            localVideoView.release();
            remoteVideoView.clearImage();
            remoteVideoView.release();
            start_finish_call.setText(getResources().getString(R.string.start_button));
            start_finish_call.setEnabled(true);
            switch_camera_btn.setEnabled(false);
            call_btn.setEnabled(false);
        });
    }

    public void viewToConnectingState() {
        runOnUiThread(() -> {
            switch_camera_btn.setEnabled(true);
            call_btn.setEnabled(true);
            start_finish_call.setEnabled(false);
        });
    }

    public void viewToConnectedState() {
        runOnUiThread(() -> {
            start_finish_call.setText(getResources().getString(R.string.hang_up));
            start_finish_call.setEnabled(true);
        });
    }

    public void createRemoteParticipantVideo(final RemoteParticipant remoteParticipant){
        Handler mainHandler = new Handler(this.getMainLooper());
        Runnable myRunnable = () -> {
            remoteParticipant.setVideoView(remoteVideoView);
            remoteVideoView.setMirror(false);
            EglBase rootEglBase = EglBase.create();
            remoteVideoView.init(rootEglBase.getEglBaseContext(), null);
            remoteVideoView.setZOrderMediaOverlay(true);
            manager_participant.setText(MANAGER_NAME);
        };
        mainHandler.post(myRunnable);
    }


    public void setRemoteMediaStream(MediaStream stream, RemoteParticipant remoteParticipant) {
        final VideoTrack videoTrack = stream.videoTracks.get(0);
        videoTrack.addSink(remoteParticipant.getVideoView());
        runOnUiThread(() -> {
            remoteParticipant.getVideoView().setVisibility(View.VISIBLE);
        });
    }

    public void askForPermissions() {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST);
        } else if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_CAMERA);
        }
    }

    private boolean arePermissionGranted() {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_DENIED) &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_DENIED);
    }

    @Override
    protected void onStop() {
//        leaveSession(false);
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        leaveSession(false);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        leaveSession(false);
        super.onBackPressed();
    }

    public SurfaceViewRenderer getlocalView(){
        return localVideoView;
    }

    public SurfaceViewRenderer getremoteView() {
        return remoteVideoView;
    }

    public void call(View view) {
        webSocket.createCall();
    }

    public void toast(String toast) {
        runOnUiThread(() -> {
            if (toastMessage != null) {
                toastMessage.cancel();
            }
            toastMessage = Toast.makeText(getApplicationContext(), toast, Toast.LENGTH_SHORT);
            toastMessage.show();
        });
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}