package com.sungbin.kurento_android.kurento;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import com.sungbin.kurento_android.usb.UsbCapturer;

import org.webrtc.AudioSource;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;

import java.util.ArrayList;
import java.util.Collection;

import static org.webrtc.ContextUtils.getApplicationContext;

public class LocalParticipant extends Participant {
    private static final String TAG = LocalParticipant.class.getSimpleName();

    private Context context;
    private SurfaceViewRenderer localVideoView;
    private SurfaceTextureHelper surfaceTextureHelper;
    private VideoCapturer videoCapturer;
    private UsbCapturer usbCapturer;

    private Collection<IceCandidate> localIceCandidates;
    private SessionDescription localSessionDescription;

    public LocalParticipant(Session session, Context context, SurfaceViewRenderer localVideoView) {
        super(session);
        this.localVideoView = localVideoView;
        this.context = context;
        this.localIceCandidates = new ArrayList<>();
        session.setLocalParticipant(this);
    }

    public void startCamera(Intent captureIntent) {

        final EglBase.Context eglBaseContext = EglBase.create().getEglBaseContext();
        PeerConnectionFactory peerConnectionFactory = this.session.getPeerConnectionFactory();

        // create AudioSource
        AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        this.audioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext);
        // create VideoCapturer

        // USB CAMERA
//        usbCapturer = new UsbCapturer(context, localVideoView);
//        VideoSource videoSource = peerConnectionFactory.createVideoSource(usbCapturer.isScreencast());
//        usbCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
//        usbCapturer.startCapture(480,640,30);


        // MY CAMERA
//        VideoCapturer videoCapturer = createCameraCapturer();
//        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
//        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
//        videoCapturer.startCapture(480, 640, 30);


        // Screen Share
        videoCapturer = new ScreenCapturerAndroid(captureIntent, new MediaProjection.Callback() {                    // Mirroing
            @Override
            public void onStop() {
                super.onStop();
            }
        });
        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
        videoCapturer.startCapture(1280, 720, 20);

        // create VideoTrack
        this.videoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);
        // display in localView
        this.videoTrack.addSink(this.localVideoView);
    }




    private VideoCapturer createCameraCapturer() {
        CameraEnumerator enumerator;
        enumerator = new Camera1Enumerator(false);
        final String[] deviceNames = enumerator.getDeviceNames();

        // Try to find front facing camera
        for (String deviceName : deviceNames) {
            Log.d(TAG , "front device name : "+deviceName);
            if (enumerator.isFrontFacing(deviceName)) {
                videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        // Front facing camera not found, try something else
        for (String deviceName : deviceNames) {
            Log.d(TAG , "back device name : "+deviceName);
            if (!enumerator.isBackFacing(deviceName)) {
                videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    public void switchCamera(){
        if (videoCapturer != null) {
            if (videoCapturer instanceof CameraVideoCapturer) {
                CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
                cameraVideoCapturer.switchCamera(null);
            } else {
                // Will not switch camera, video capturer is not a camera
            }
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        if (videoTrack != null) {
            videoTrack.removeSink(localVideoView);
//            usbCapturer.dispose();
//            usbCapturer = null;
            videoCapturer.dispose();
            videoCapturer = null;
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
    }
}
