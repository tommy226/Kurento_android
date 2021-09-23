package com.sungbin.kurento_android.kurento;
import org.webrtc.SurfaceViewRenderer;

public class RemoteParticipant extends Participant {
    private SurfaceViewRenderer remoteVideoView;

    public RemoteParticipant(Session session) {
        super(session);
        this.session.setRemoteParticipant(this);
    }

    public SurfaceViewRenderer getVideoView() {
        return this.remoteVideoView;
    }

    public void setVideoView(SurfaceViewRenderer videoView) {
        this.remoteVideoView = videoView;
    }


    @Override
    public void dispose() {
        super.dispose();
    }
}
