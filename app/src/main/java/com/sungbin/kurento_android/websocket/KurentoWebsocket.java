package com.sungbin.kurento_android.websocket;

import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import com.neovisionaries.ws.client.ThreadType;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.neovisionaries.ws.client.WebSocketListener;
import com.neovisionaries.ws.client.WebSocketState;
import com.sungbin.kurento_android.MainActivity;
import com.sungbin.kurento_android.kurento.LocalParticipant;
import com.sungbin.kurento_android.kurento.RemoteParticipant;
import com.sungbin.kurento_android.kurento.Session;
import com.sungbin.kurento_android.observers.CustomSdpObserver;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.RtpTransceiver;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class KurentoWebsocket extends AsyncTask<MainActivity, Void, Void> implements WebSocketListener {
    private final String TAG = KurentoWebsocket.class.getSimpleName();

    private final TrustManager[] trustManagers = new TrustManager[]{new X509TrustManager() {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
            Log.i(TAG, ": authType: " + authType);
        }

        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
            Log.i(TAG, ": authType: " + authType);
        }
    }};
    private Session session;
    private String kurentoUrl;
    private MainActivity activity;
    private WebSocket websocket;
    private boolean websocketCancelled = false;

    private String managerName;
    private String workerName;

    public KurentoWebsocket(Session session, String kurentoUrl, MainActivity activity,
                            String managerName, String workerName) {
        this.session = session;
        this.kurentoUrl = kurentoUrl;
        this.activity = activity;
        this.managerName = managerName;
        this.workerName = workerName;
    }

    public void sendMessage(JSONObject jsonObject){
        Log.d(TAG , "SEND Message : " + jsonObject.toString());
        websocket.sendText(jsonObject.toString());
    }

    @Override
    public void onTextMessage(WebSocket websocket, String text) throws Exception {
        Log.i(TAG, "Text Message :" + text);
        JSONObject jsonObject = new JSONObject(text);

        String responseID = (String) jsonObject.get("id");
        Log.i(TAG, "Server responseID :" + responseID);
        switch (responseID){
            case "registerResponse":                // 등록완료
                activity.viewToConnectedState();
                activity.toast("등록 성공");
                break;
            case "callResponse":                // 멘토링 연결 응답
                String response = (String) jsonObject.get("response");

                if(response.equals("accepted")){
                    activity.toast("멘토링 수락 되었음");
                    String sdpAnswer = (String) jsonObject.get("sdpAnswer");
                    callResponse(sdpAnswer);
                }else{
                    activity.toast("멘토링 거절 되었음");
                    activity.leaveSession(true);
                    // 거절
                }
                break;
            case "incomingCall":                // 매니저에서 멘토링 요청

                break;
            case "startCommunication":                // 멘토링 시작

                break;
            case "stopCommunication":                // 멘토링 종료
                activity.toast("멘토링 종료");
                activity.leaveSession(true);
                break;
            case "iceCandidate":                // iceCandidate
                Log.d(TAG, "iceCandidate ****************************************");
                break;
            default:
                Log.d(TAG, "SERVER RESPONSE ERROR");
        }
    }

    public void register() throws JSONException {
        JSONObject registerJSON = new JSONObject();
        registerJSON.put("id", "register");
        registerJSON.put("name", workerName);

        sendMessage(registerJSON);
//        mentoringRequest();
    }

    public void createLocal() {
        PeerConnection localPeerConnection = session.createLocalPeerConnection();   // 로컬 피어 커넥션 생성
        final LocalParticipant localParticipant = this.session.getLocalParticipant();
//        localParticipant.startCamera();
        localPeerConnection.addTrack(localParticipant.getAudioTrack());
        localPeerConnection.addTrack(localParticipant.getVideoTrack());
        for (RtpTransceiver transceiver : localPeerConnection.getTransceivers()) {
            transceiver.setDirection(RtpTransceiver.RtpTransceiverDirection.SEND_RECV);
        }
        localParticipant.setPeerConnection(localPeerConnection);

        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));
        session.createOfferForPublishing(sdpConstraints);
    }

    public void createRemote(){

        final RemoteParticipant remoteParticipant = new RemoteParticipant(this.session);
        this.activity.createRemoteParticipantVideo(remoteParticipant);
        this.session.createRemotePeerConnection(); // 리모트 피어 커넥션 생성

        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));

        remoteParticipant.getPeerConnection().createOffer(new CustomSdpObserver("create remote"){
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                remoteParticipant.getPeerConnection().setLocalDescription(new CustomSdpObserver("createOffer_remoteSetRemoteDesc"), sessionDescription);
                try {
                    call(sessionDescription);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onCreateFailure(String s) {
                super.onCreateFailure(s);
            }
        },sdpConstraints);
    }

    public void createCall(){
        createLocal();
        createRemote();
    }

    public void call(SessionDescription sessionDescription) throws JSONException {
        JSONObject publishVideoParams = new JSONObject();
        publishVideoParams.put("id", "call");
        publishVideoParams.put("from",workerName);
        publishVideoParams.put("to",managerName);
        publishVideoParams.put("sdpOffer", sessionDescription.description);

        sendMessage(publishVideoParams);
    }

    public void callResponse(String sdpAnswer){
        LocalParticipant localParticipant = this.session.getLocalParticipant();
        RemoteParticipant remoteParticipant = this.session.getRemoteParticipant();

        SessionDescription remoteSdpAnswer = new SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer);

        localParticipant.getPeerConnection().setRemoteDescription(new CustomSdpObserver("publishVideo_setRemoteDescription"), remoteSdpAnswer);
        remoteParticipant.getPeerConnection().setRemoteDescription(new CustomSdpObserver("Manager_setRemoteDescription"), remoteSdpAnswer);
    }

    public void leaveRoom(boolean managerCall) throws JSONException {
        JSONObject closeJSON = new JSONObject();
        if(!managerCall){
            closeJSON.put("id", "stopMessageId");
            sendMessage(closeJSON);
        }
    }

    public void onIceCandidate(IceCandidate iceCandidate) throws JSONException {
        JSONObject onIceCandidateParams = new JSONObject();
        JSONObject tempJSON = new JSONObject();
        tempJSON.put("id","onIceCandidate");
        onIceCandidateParams.put("candidate", iceCandidate.sdp);
        onIceCandidateParams.put("sdpMid", iceCandidate.sdpMid);
        onIceCandidateParams.put("sdpMLineIndex", Integer.toString(iceCandidate.sdpMLineIndex));

        tempJSON.put("candidate", onIceCandidateParams);

        sendMessage(tempJSON);
    }

    @Override
    public void onStateChanged(WebSocket websocket, WebSocketState newState) throws Exception {
        Log.i(TAG, "State changed: " + newState.name());
    }

    @Override
    public void onConnected(WebSocket ws, Map<String, List<String>> headers) throws
            Exception {
        Log.i(TAG, "Connected");
        register();
    }

    @Override
    public void onConnectError(WebSocket websocket, WebSocketException cause) throws Exception {
        Log.e(TAG, "Connect error: " + cause);
    }

    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame
            serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
        Log.e(TAG, "Disconnected " + serverCloseFrame.getCloseReason() + " " + clientCloseFrame.getCloseReason() + " " + closedByServer);
    }

    @Override
    public void onFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Frame");
    }

    @Override
    public void onContinuationFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Continuation Frame");
    }

    @Override
    public void onTextFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Text Frame");
    }

    @Override
    public void onBinaryFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Binary Frame");
    }

    @Override
    public void onCloseFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Close Frame");
    }

    @Override
    public void onPingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Ping Frame");
    }

    @Override
    public void onPongFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Pong Frame");
    }

    @Override
    public void onTextMessage(WebSocket websocket, byte[] data) throws Exception {

    }

    @Override
    public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {
        Log.i(TAG, "Binary Message");
    }

    @Override
    public void onSendingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Sending Frame");
    }

    @Override
    public void onFrameSent(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Frame sent");
    }

    @Override
    public void onFrameUnsent(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Frame unsent");
    }

    @Override
    public void onThreadCreated(WebSocket websocket, ThreadType threadType, Thread thread) throws
            Exception {
        Log.i(TAG, "Thread created");
    }

    @Override
    public void onThreadStarted(WebSocket websocket, ThreadType threadType, Thread thread) throws
            Exception {
        Log.i(TAG, "Thread started");
    }

    @Override
    public void onThreadStopping(WebSocket websocket, ThreadType threadType, Thread thread) throws
            Exception {
        Log.i(TAG, "Thread stopping");
    }

    @Override
    public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
        Log.e(TAG, "Error!");
    }

    @Override
    public void onFrameError(WebSocket websocket, WebSocketException cause, WebSocketFrame
            frame) throws Exception {
        Log.e(TAG, "Frame error!");
    }

    @Override
    public void onMessageError(WebSocket websocket, WebSocketException
            cause, List<WebSocketFrame> frames) throws Exception {
        Log.e(TAG, "Message error! " + cause);
    }

    @Override
    public void onMessageDecompressionError(WebSocket websocket, WebSocketException cause,
                                            byte[] compressed) throws Exception {
        Log.e(TAG, "Message decompression error!");
    }

    @Override
    public void onTextMessageError(WebSocket websocket, WebSocketException cause, byte[] data) throws
            Exception {
        Log.e(TAG, "Text message error! " + cause);
    }

    @Override
    public void onSendError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) throws
            Exception {
        Log.e(TAG, "Send error! " + cause);
    }

    @Override
    public void onUnexpectedError(WebSocket websocket, WebSocketException cause) throws
            Exception {
        Log.e(TAG, "Unexpected error! " + cause);
    }

    @Override
    public void handleCallbackError(WebSocket websocket, Throwable cause) throws Exception {
        Log.e(TAG, "Handle callback error! " + cause);
    }

    @Override
    public void onSendingHandshake(WebSocket websocket, String requestLine, List<String[]>
            headers) throws Exception {
        Log.i(TAG, "Sending Handshake! Hello!");
    }

    public void setWebsocketCancelled(boolean websocketCancelled) {
        this.websocketCancelled = websocketCancelled;
    }

    public void disconnect() {
        this.websocket.disconnect();
    }

    @Override
    protected Void doInBackground(MainActivity... mainActivities) {
        try {
            WebSocketFactory factory = new WebSocketFactory();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, new java.security.SecureRandom());
            factory.setSSLContext(sslContext);
            factory.setVerifyHostname(false);
            websocket = factory.createSocket(kurentoUrl);
            websocket.addListener(this);
            websocket.connect();
            Log.d(TAG, "Socket URL : " + kurentoUrl);
        } catch (IOException | WebSocketException | KeyManagementException | NoSuchAlgorithmException e) {
            Log.e("WebSocket error", e.getMessage());
            Handler mainHandler = new Handler(activity.getMainLooper());
            Runnable myRunnable = () -> {
                Toast toast = Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG);
                toast.show();
                activity.leaveSession(false);
            };
            mainHandler.post(myRunnable);
            websocketCancelled = true;
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(Void... progress) {
        Log.i(TAG, "PROGRESS " + Arrays.toString(progress));
    }
}
