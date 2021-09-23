package com.sungbin.kurento_android.kurento;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import com.sungbin.kurento_android.MainActivity;
import com.sungbin.kurento_android.observers.CustomPeerConnectionObserver;
import com.sungbin.kurento_android.observers.CustomSdpObserver;
import com.sungbin.kurento_android.websocket.KurentoWebsocket;

import org.json.JSONException;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SoftwareVideoDecoderFactory;
import org.webrtc.SoftwareVideoEncoderFactory;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Session {
    private final String TAG = Session.class.getSimpleName();

    private LocalParticipant localParticipant;
    private RemoteParticipant remoteParticipant;

//    private Map<String, RemoteParticipant> remoteParticipants = new HashMap<>();
    private LinearLayout views_container;
    private PeerConnectionFactory peerConnectionFactory;
    private KurentoWebsocket websocket;
    private MainActivity activity;

    public Session(LinearLayout views_container, MainActivity activity) {
        this.views_container = views_container;
        this.activity = activity;

        PeerConnectionFactory.InitializationOptions.Builder optionsBuilder = PeerConnectionFactory.InitializationOptions.builder(activity.getApplicationContext());
        optionsBuilder.setEnableInternalTracer(true);
        PeerConnectionFactory.InitializationOptions opt = optionsBuilder.createInitializationOptions();
        PeerConnectionFactory.initialize(opt);
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();

        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;
        encoderFactory = new SoftwareVideoEncoderFactory();
        decoderFactory = new SoftwareVideoDecoderFactory();

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setOptions(options)
                .createPeerConnectionFactory();

    }

    public void setWebSocket(KurentoWebsocket websocket) {
        this.websocket = websocket;
    }

    public PeerConnection createLocalPeerConnection() {
        final List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection.IceServer stun = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                .setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
                .createIceServer();

        PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder("turn:url")
                .setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
                .setUsername("id")
                .setPassword("password")
                .createIceServer();

        iceServers.add(stun);
        iceServers.add(iceServer);

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        rtcConfig.enableDtlsSrtp = true;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new CustomPeerConnectionObserver("local") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                try {
                    websocket.onIceCandidate(iceCandidate);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        return peerConnection;
    }

    public void createRemotePeerConnection() {
        final List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection.IceServer stun = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                .setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
                .createIceServer();

                PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder("turn:url")
                .setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
                .setUsername("id")
                .setPassword("password")
                .createIceServer();


        iceServers.add(stun);
        iceServers.add(iceServer);

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        rtcConfig.enableDtlsSrtp = true;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new CustomPeerConnectionObserver("remotePeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                try {
                    websocket.onIceCandidate(iceCandidate);
                    Log.d(TAG, "remote ice : "+iceCandidate);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            ////
            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                super.onAddTrack(rtpReceiver, mediaStreams);
                Log.d(TAG, "onAddTrack");
                if (!(mediaStreams.length == 0)) {
                    activity.setRemoteMediaStream(mediaStreams[0], remoteParticipant);
                }
            }

            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange");
                if (PeerConnection.SignalingState.STABLE.equals(signalingState)) {
                    Iterator<IceCandidate> it = remoteParticipant.getIceCandidateList().iterator();
                    while (it.hasNext()) {
                        IceCandidate candidate = it.next();
                        remoteParticipant.getPeerConnection().addIceCandidate(candidate);
                        it.remove();
                    }
                }
            }
        });

        peerConnection.addTrack(localParticipant.getAudioTrack());//Add audio track to create transReceiver
        peerConnection.addTrack(localParticipant.getVideoTrack());//Add video track to create transReceiver

        for (RtpTransceiver transceiver : peerConnection.getTransceivers()) {
            //We set both audio and video in receive only mode
            transceiver.setDirection(RtpTransceiver.RtpTransceiverDirection.SEND_RECV);
        }

        this.remoteParticipant.setPeerConnection(peerConnection);
    }

    public void createOfferForPublishing(MediaConstraints constraints) {
        localParticipant.getPeerConnection().createOffer(new CustomSdpObserver("createOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                Log.i("createOffer SUCCESS", sessionDescription.toString());
                localParticipant.getPeerConnection().setLocalDescription(new CustomSdpObserver("createOffer_localSetLocalDesc"), sessionDescription);
            }
            @Override
            public void onCreateFailure(String s) {
                Log.e("createOffer ERROR", s);
            }

        }, constraints);


    }

    public void createAnswerForSubscribing(MediaConstraints constraints) {
        remoteParticipant.getPeerConnection().createAnswer(new CustomSdpObserver("createAnswerSubscribing") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                Log.i("createAnswer SUCCESS", sessionDescription.toString());
                remoteParticipant.getPeerConnection().setLocalDescription(new CustomSdpObserver("createAnswerSubscribing_setRemoteDescription"), sessionDescription);

                // 이쪽에서 보내서 받아야하나?
            }
            @Override
            public void onCreateFailure(String s) {
                Log.e("createAnswer ERROR", s);
            }

        }, constraints);
    }


    public LocalParticipant getLocalParticipant() {
        return this.localParticipant;
    }

    public void setLocalParticipant(LocalParticipant localParticipant) {
        this.localParticipant = localParticipant;
    }

    public RemoteParticipant getRemoteParticipant() {
        return remoteParticipant;
    }

    public void setRemoteParticipant(RemoteParticipant remoteParticipant) {
        this.remoteParticipant = remoteParticipant;
    }

//    public RemoteParticipant getRemoteParticipant(String id) {
//        return this.remoteParticipants.get(id);
//    }

    public PeerConnectionFactory getPeerConnectionFactory() {
        return this.peerConnectionFactory;
    }

//    public void addRemoteParticipant(RemoteParticipant remoteParticipant) {
//        this.remoteParticipants.put(remoteParticipant.getConnectionId(), remoteParticipant);
//    }
//
//    public RemoteParticipant removeRemoteParticipant(String id) {
//        return this.remoteParticipants.remove(id);
//    }

    public void switchCamera(){
        this.localParticipant.switchCamera();
    }

    public void leaveSession(boolean managerCall) {
        AsyncTask.execute(() -> {
            websocket.setWebsocketCancelled(true);
            if (websocket != null) {
                try {
                    websocket.leaveRoom(managerCall);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                websocket.disconnect();
            }
            this.localParticipant.dispose();
            this.remoteParticipant.dispose();
        });
        this.activity.runOnUiThread(() -> {
//            for (RemoteParticipant remoteParticipant : remoteParticipants.values()) {                 // 그룹콜 용
//                if (remoteParticipant.getPeerConnection() != null) {
//                    remoteParticipant.getPeerConnection().close();
//                }
//                views_container.removeView(remoteParticipant.getView());
//            }
            activity.getlocalView().clearImage();
            activity.getremoteView().clearImage();
//            remoteParticipants.clear();
        });
        AsyncTask.execute(() -> {
            if (peerConnectionFactory != null) {
                peerConnectionFactory.dispose();
                peerConnectionFactory = null;
            }
        });
    }

    public void removeView(View view) {
        this.views_container.removeView(view);
    }

}
