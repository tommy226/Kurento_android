package com.sungbin.kurento_android.usb;

import android.content.Context;
import android.hardware.usb.UsbDevice;

import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import org.webrtc.CapturerObserver;
import org.webrtc.NV21Buffer;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class UsbCapturer implements VideoCapturer, USBMonitor.OnDeviceConnectListener, IFrameCallback {
    private static final String TAG = UsbCapturer.class.getSimpleName();

    private Context context;
    private USBMonitor monitor;
    private SurfaceViewRenderer svVideoRender;
    private SurfaceTextureHelper surfaceTextureHelper;
    private CapturerObserver capturerObserver;
    private Executor executor = Executors.newSingleThreadExecutor();

    public UsbCapturer(Context context, SurfaceViewRenderer svVideoRender) {
        this.context = context;
        this.svVideoRender = svVideoRender;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                monitor = new USBMonitor(context, UsbCapturer.this);
                monitor.register();
            }
        });
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context context, CapturerObserver capturerObserver) {
        this.context = context;
        this.surfaceTextureHelper = surfaceTextureHelper;
        this.capturerObserver = capturerObserver;
    }

    @Override
    public void startCapture(int width, int height, int framerate) {

    }

    @Override
    public void stopCapture() throws InterruptedException {
        if(camera != null){
            camera.stopPreview();
            camera.close();
            camera.destroy();
        }
    }

    @Override
    public void changeCaptureFormat(int i, int i1, int i2) {

    }

    @Override
    public void dispose() {
        monitor.unregister();
        monitor.destroy();

    }

    @Override
    public boolean isScreencast() {
        return false;
    }

    @Override
    public void onAttach(UsbDevice device) {
        monitor.requestPermission(device);
    }

    @Override
    public void onDettach(UsbDevice device) {

    }

    UVCCamera camera;

    @Override
    public void onConnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                camera = new UVCCamera();
                camera.open(ctrlBlock);
                try {
                    camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
                } catch (final IllegalArgumentException e) {
                    try {
                        camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
                    } catch (final IllegalArgumentException e1) {
                        camera.destroy();
                        camera = null;
                    }
                }
                camera.setPreviewDisplay(svVideoRender.getHolder());
                camera.setFrameCallback(UsbCapturer.this, UVCCamera.PIXEL_FORMAT_YUV420SP);
                camera.startPreview();
            }
        });
    }

    @Override
    public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
    }

    @Override
    public void onCancel(UsbDevice device) {
    }

    @Override
    public void onFrame(ByteBuffer frame) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                byte[] imageArray = new byte[frame.remaining()];
                frame.get(imageArray);
                NV21Buffer nv21Buffer = new NV21Buffer(imageArray, UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, null);
                VideoFrame videoFrame = new VideoFrame(nv21Buffer, 0, System.nanoTime());
                capturerObserver.onFrameCaptured(videoFrame);
            }
        });
    }
}