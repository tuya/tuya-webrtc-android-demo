package org.appspot.apprtc;

import android.nfc.Tag;
import android.text.TextUtils;
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.acl.LastOwnerException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.webrtc.ContextUtils.getApplicationContext;

public class MqttRTCClient implements AppRTCClient,MqttCallbackExtended
{
    private MqttAndroidClient _mqttAndroidClient;

    private SignalingEvents _events;

    private String _sessionId ;
    public JSONObject _localTocken ;
    public JSONObject _webrtcConfig ;
    public JSONObject _mqttConfig ;
    RoomConnectionParameters _connectParameters ;


    public MqttRTCClient(SignalingEvents events)
    {
        this._events = events;
    }

    public static String getRandomString(int length) {
        String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(62);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }


    public static String getCurrentTimeString() {
        long time = System.currentTimeMillis() ;
        String strTime = String.valueOf(time) ;
        return  strTime ;
    }

    public static int getCurrentTimeInS() {
        long time = System.currentTimeMillis() ;
        return (int) (time / 1000 );
    }

    public String md5(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(string.getBytes());
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) {
                String temp = Integer.toHexString(b & 0xff);
                if (temp.length() == 1) {
                    temp = "0" + temp;
                }
                result.append(temp);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    String getSignForGetToken(String clientId, String secret, String ts)
    {
        String astring = clientId + secret+ts ;
        Log.d("MQTT","getSignForGetToken astring:" + astring) ;
        String md5String =  md5(astring);
        return  md5String.toUpperCase();
    }

    String getSignForOthers(String clientId, String secret, String ts, String accessToken)
    {
        String astring = clientId + accessToken + secret + ts ;
        String md5String =  md5(astring);
        return  md5String.toUpperCase();
    }

    Boolean getToken(String clientid, String secret, String code) {
/*
     curl --location --request GET "{{url}}/v1.0/token?code=669c3e1f3a4e38103bf6e9544ea0d311&grant_type=2" \
     --header "client_id: {{clientId}}" \
     --header "sign: {{easy_sign}}" \
     --header "t: {{timestamp}}"
*/

        try {
            Log.d("MQTT"," get Token clientId:" + clientid + " secret:" + secret + " Code:" + code) ;
            String strUrl = "https://openapi-cn.wgine.com/v1.0/token?code=" + code + "&grant_type=2" ;
            URL url = new URL(strUrl);
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setRequestMethod("GET");
            // for header.
            String ts = getCurrentTimeString() ;
            String sign = getSignForGetToken(clientid,secret,ts);
            // sign
            connection.setRequestProperty("sign",sign);
            // client id
            connection.setRequestProperty("client_id",clientid);
            // ts
            connection.setRequestProperty("t",ts);

            Log.d("MQTT" ," get Token sign:" + sign + " client_id :" + clientid + " ts:" +ts );
            connection.connect();
            int res = connection.getResponseCode();
            if (res == HttpURLConnection.HTTP_OK)
            {
                //得到响应流
                InputStream inputStream = connection.getInputStream();
                //将响应流转换成字符串
                String result = IOUtils.toString(inputStream,"UTF-8");
                Log.d("MQTT"," get token result:" + result);
                JSONObject json = new JSONObject(result);
                String success = json.getString("success");
                if (Boolean.parseBoolean(success)) {
                    _localTocken = json.getJSONObject("result");
                    Log.d("MQTT","Token:" + result);
                    return true;
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


    boolean getWebrtcConfig(String clientid, String secret, String accessToken, String deviceid)
    {
/*
     curl --location --request GET "{{url}}/v1.0/devices/vdevo157163890481773/camera-config?type=rtc" \
     --header "client_id: {{clientId}}" \
     --header "access_token: {{easy_access_token}}" \
     --header "sign: {{easy_sign}}" \
     --header "t: {{timestamp}}"
*/
        try{
            String strUrl = "https://openapi-cn.wgine.com/v1.0/devices/" + deviceid + "/camera-config?type=rtc" ;
            URL url = new URL(strUrl);
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(30000);
            connection.setRequestMethod("GET");
            // for header.
            String ts = getCurrentTimeString() ;
            String sign = getSignForOthers(clientid,secret,ts,accessToken);

            // sign
            connection.setRequestProperty("sign",sign);
            // client id
            connection.setRequestProperty("client_id",clientid);
            // ts
            connection.setRequestProperty("t",ts);

            // access token
            connection.setRequestProperty("access_token",accessToken);
            connection.connect();
            int res = connection.getResponseCode();
            if (res == HttpURLConnection.HTTP_OK)
            {
                //得到响应流
                InputStream inputStream = connection.getInputStream();
                //将响应流转换成字符串
                String result = IOUtils.toString(inputStream,"UTF-8");
                JSONObject json = new JSONObject(result);
                String success = json.getString("success");
                if (Boolean.parseBoolean(success)) {
                    _webrtcConfig = json.getJSONObject("result");
                    Log.d("MQTT","webrtc config:" + result);
                    return  true;
                }
                Log.d("MQTT","===== getWebrtcConfig  failed......") ;
                return false ;
            }
        }catch (Exception e)
        {

        }
        return false;
    }


    boolean getMqttConfig(String clientid, String secret, String accessToken){
/*
     curl --location --request GET "{{url}}/v1.0/access/11/config?type=websocket" \
     --header "client_id: {{clientId}}" \
     --header "access_token: {{easy_access_token}}" \
     --header "sign: {{easy_sign}}" \
     --header "t: {{timestamp}}"
*/
        try {
            String strUrl = "https://openapi-cn.wgine.com/v1.0/access/11/config" ;
            URL url = new URL(strUrl);
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(30000);
            connection.setRequestMethod("GET");
            // for header.
            String ts = getCurrentTimeString() ;
            String sign = getSignForOthers(clientid,secret,ts,accessToken);

            // sign
            connection.setRequestProperty("sign",sign);
            // client id
            connection.setRequestProperty("client_id",clientid);
            // ts
            connection.setRequestProperty("t",ts);
            // access token
            connection.setRequestProperty("access_token",accessToken);
            connection.connect();
            int res = connection.getResponseCode();
            if (res == HttpURLConnection.HTTP_OK)
            {
                //得到响应流
                InputStream inputStream = connection.getInputStream();
                //将响应流转换成字符串
                String result = IOUtils.toString(inputStream,"UTF-8");
                JSONObject json = new JSONObject(result);
                String success = json.getString("success");
                if (Boolean.parseBoolean(success)) {
                    _mqttConfig = json.getJSONObject("result");
                    Log.d("MQTT","mqtt config:" + result);
                    return true;
                }
                Log.d("MQTT","===== getMqttConfig  failed......") ;
            }
        }catch (Exception e)
        {

        }
        return false ;
    }

    private IMqttActionListener mqttActionListener = new IMqttActionListener() {
        @Override
        public void onSuccess(IMqttToken asyncActionToken) {

        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {

        }
    };

    // AppRTCClient
    @Override
    public void connectToRoom(RoomConnectionParameters connectionParameters) {
        _connectParameters = connectionParameters ;
        _sessionId = getRandomString(32) ;
        new Thread(new Runnable() {
            @Override
            public void run() {
                connectToRoomInternal();
            }
        }).start();
    }

    private void connectToRoomInternal()
    {
        Log.d("MQTT","connectToRoomInternal......");
        Boolean result = getToken(_connectParameters.clientId,_connectParameters.secret,_connectParameters.AuthCode);
        if (result)
//        if (true)
        {
            try {
                String accessToken = _localTocken.getString("access_token");
//                String accessToken = "8377ccc5c52515f7d31d5a6fb8986f07";
                Boolean flag0 = getWebrtcConfig(_connectParameters.clientId,_connectParameters.secret,accessToken,_connectParameters.deviceid);
                Boolean flag1 = getMqttConfig(_connectParameters.clientId,_connectParameters.secret,accessToken) ;
                if (flag0 && flag1){
                    String clientId = _mqttConfig.getString("client_id");
                    String url =  _mqttConfig.getString("url");
                    String uid = _mqttConfig.getString("username").substring(6);
                    String topic = "/av/u/" + uid ;
                    String userNmae = _mqttConfig.getString("username");
                    String password = _mqttConfig.getString("password");

                    String tmpDir = System.getProperty("java.io.tmpdir");
                    MqttDefaultFilePersistence dataStore = new MqttDefaultFilePersistence(tmpDir);

                    Log.d("MQTT"," connect to MQTT clientId:" + clientId + " url :" + url + " userName:" + userNmae + " password:" + password +" topic:" + topic);
                    MqttConnectOptions conOpt = new MqttConnectOptions();
                    conOpt.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1);
                    conOpt.setCleanSession(false);
                    conOpt.setKeepAliveInterval(20);
                    conOpt.setAutomaticReconnect(true);
                    conOpt.setUserName(userNmae);
                    conOpt.setPassword(password.toCharArray());

                    _mqttAndroidClient = new MqttAndroidClient(getApplicationContext(),url,clientId);
                    _mqttAndroidClient.setCallback(this);
                    _mqttAndroidClient.connect(conOpt, null, new IMqttActionListener() {
                        @Override
                        public void onSuccess(IMqttToken asyncActionToken) {
                            subscribeToTopic();
                        }

                        @Override
                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                            Log.d("MQTT","mqtt connect failed......");
                        }
                    });
                }
            }catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    // Put a |key|->|value| mapping in |json|.
    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static String hmacSha256(String KEY, String VALUE) {
        return hmacSha(KEY, VALUE, "HmacSHA256");
    }

    private static String hmacSha(String KEY, String VALUE, String SHA_TYPE) {
        try {
            SecretKeySpec signingKey = new SecretKeySpec(KEY.getBytes("UTF-8"), SHA_TYPE);
            Mac mac = Mac.getInstance(SHA_TYPE);
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(VALUE.getBytes("UTF-8"));

            byte[] hexArray = {
                    (byte)'0', (byte)'1', (byte)'2', (byte)'3',
                    (byte)'4', (byte)'5', (byte)'6', (byte)'7',
                    (byte)'8', (byte)'9', (byte)'a', (byte)'b',
                    (byte)'c', (byte)'d', (byte)'e', (byte)'f'
            };
            byte[] hexChars = new byte[rawHmac.length * 2];
            for ( int j = 0; j < rawHmac.length; j++ ) {
                int v = rawHmac[j] & 0xFF;
                hexChars[j * 2] = hexArray[v >>> 4];
                hexChars[j * 2 + 1] = hexArray[v & 0x0F];
            }
            return new String(hexChars);
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    public String getAuthCode()
    {
        try{
            String p2p_password = _webrtcConfig.getJSONObject("configs").getString("password");
            String hashkey = p2p_password + ":" + _connectParameters.localKey ;
            Log.d("MQTT","===== getAuthCode p2p password:" + p2p_password + "  hashkey:" + hashkey + " deviceid:" + _connectParameters.deviceid);

            SecretKeySpec signingKey = new SecretKeySpec(hashkey.getBytes("UTF-8"), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(_connectParameters.deviceid.getBytes("UTF-8"));
//            String auth = Base64.getEncoder().encodeToString(rawHmac);

            byte[] byteAuth = TYBase64.encodeBase64(rawHmac) ;
            String auth = new String(byteAuth);
            Log.d("MQTT","===== getAuthCode auth code:" + auth);
            return auth ;
        }catch (Exception e)
        {

        }
        return "" ;
    }

    @Override
    public void sendOfferSdp(SessionDescription sdp) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject message = new JSONObject();
                    jsonPut(message,"protocol",302);
                    jsonPut(message,"t",getCurrentTimeInS());

                    // configuration
                    JSONObject data = new JSONObject();

                    // header
                    JSONObject header = new JSONObject();
                    String uid = _mqttConfig.getString("username").substring(6) ;
                    jsonPut(header,"from",uid);
                    jsonPut(header,"to",_connectParameters.deviceid);
                    jsonPut(header,"type","offer");
                    jsonPut(header,"moto_id","moto_pre_cn002");
                    jsonPut(header,"sessionid",_sessionId);

                    jsonPut(data,"header",header);

                    // msg
                    JSONObject msg = new JSONObject();
                    jsonPut(msg,"mode","webrtc");
                    JSONArray ices = _webrtcConfig.getJSONObject("configs").getJSONObject("p2p_config").getJSONArray("ices");
                    jsonPut(msg,"token",ices);
                    jsonPut(msg,"sdp",sdp.description);
                    String auth = getAuthCode() ;
                    Log.d("MQTT","===== getAuthCode the auth:" + auth);
                    jsonPut(msg,"auth",auth);

                    jsonPut(data,"msg",msg);
                    jsonPut(message,"data",data);

                    String jsonString = message.toString();
                    MqttMessage mqttMsg = new MqttMessage();
                    mqttMsg.setQos(1);
                    mqttMsg.setRetained(false);
                    mqttMsg.setPayload(jsonString.getBytes());

                    String moto_topic = "/av/moto/moto_pre_cn002/u/" + _connectParameters.deviceid;
                    Log.d("MQTT","====== send offer:" + jsonString);
                    _mqttAndroidClient.publish(moto_topic, mqttMsg, null, new IMqttActionListener() {
                        @Override
                        public void onSuccess(IMqttToken asyncActionToken) {
                            Log.d("MQTT","Publish succeeded...topic :" + moto_topic);
                        }

                        @Override
                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                            Log.d("MQTT","Publish failed...topic :" + moto_topic);
                        }
                    });
                }catch (Exception e)
                {

                }

            }
        }).start();
    }

    @Override
    public void sendAnswerSdp(SessionDescription sdp) {

    }

    @Override
    public void sendLocalIceCandidate(IceCandidate candidate) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject message = new JSONObject();
                    jsonPut(message,"protocol",302);
                    jsonPut(message,"t",getCurrentTimeInS());

                    // configuration
                    JSONObject data = new JSONObject();

                    // header
                    JSONObject header = new JSONObject();
                    String uid = _mqttConfig.getString("username").substring(6) ;
                    jsonPut(header,"from",uid);
                    jsonPut(header,"to",_connectParameters.deviceid);
                    jsonPut(header,"type","candidate");
                    jsonPut(header,"moto_id","moto_pre_cn002");
                    jsonPut(header,"sessionid",_sessionId);
                    jsonPut(data,"header",header);

                    // msg
                    JSONObject msg = new JSONObject();
                    jsonPut(msg,"mode","webrtc");
                    String strCandidate = "a=" + candidate.sdp ;
                    jsonPut(msg,"candidate",strCandidate);
                    jsonPut(data,"msg",msg);

                    jsonPut(message,"data",data);

                    String jsonString = message.toString();
                    MqttMessage mqttMsg = new MqttMessage();
                    mqttMsg.setQos(1);
                    mqttMsg.setRetained(false);
                    mqttMsg.setPayload(jsonString.getBytes());

                    String moto_topic = "/av/moto/moto_pre_cn002/u/" + _connectParameters.deviceid;
                    _mqttAndroidClient.publish(moto_topic, mqttMsg, null, new IMqttActionListener() {
                        @Override
                        public void onSuccess(IMqttToken asyncActionToken) {
                            Log.d("MQTT","Publish succeeded...topic :" + moto_topic);
                        }

                        @Override
                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                            Log.d("MQTT","Publish failed...topic :" + moto_topic);
                        }
                    });
                }catch (Exception e)
                {

                }

            }
        }).start();
    }

    @Override
    public void sendLocalIceCandidateRemovals(IceCandidate[] candidates) {

    }

    @Override
    public void disconnectFromRoom() {

    }

    public void subscribeToTopic(){

        try {
            String uid = _mqttConfig.getString("username").substring(6) ;
            String topic = "/av/u/" + uid ;
            _mqttAndroidClient.subscribe(topic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    try {
                        // subscribe success we will send offer
                        SignalingParameters signalParameters = new SignalingParameters();
                        signalParameters.initiator = false ;
                        JSONObject ices = _webrtcConfig.getJSONObject("configs").getJSONObject("p2p_config");
                        JSONArray icesArray = ices.getJSONArray("ices");
                        int len = icesArray.length() ;
                        List<PeerConnection.IceServer> iceServers = new ArrayList<PeerConnection.IceServer>();
                        for (int i = 0 ; i < icesArray.length();i++)
                        {
                            JSONObject ice = (JSONObject)icesArray.get(i);
                            String url = ice.getString("urls");
                            if (url.contains("stun")){
                                PeerConnection.IceServer iceServer = new PeerConnection.IceServer(url);
                                iceServers.add(iceServer);
                            }else if(url.contains("turn"))
                            {
                                String username = ice.getString("username");
                                String credential = ice.getString("credential");
                                PeerConnection.IceServer iceServer = new PeerConnection.IceServer(url,username,credential);
                                iceServers.add(iceServer);
                            }
                        }
                        signalParameters.iceServers = iceServers ;
                        signalParameters.clientId = _connectParameters.clientId ;
                        _events.onConnectedToRoom(signalParameters);
                        Log.d("MQTT","Subscribe success. topic:" + topic);
                    }catch (Exception e)
                    {
                        e.printStackTrace();
                    }

                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d("MQTT","Subscribe failed. topic:" + topic);
                }
            });
        } catch (Exception ex){
            System.err.println("Exception whilst subscribing");
            ex.printStackTrace();
        }
    }

    // MqttCallbackExtended
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        Log.d("MQTT","connectComplete reconnect:" + reconnect + " server url:" + serverURI);
        if (reconnect)
        {
            subscribeToTopic();
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        Log.d("MQTT","connectionLost " + cause.toString());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String jsonString = message.toString() ;
        JSONObject json = new JSONObject(jsonString);
        if (json != null)
        {
            String type = json.getJSONObject("data").getJSONObject("header").getString("type");
            if (type.equals("answer")){

                String sdp = json.getJSONObject("data").getJSONObject("msg").getString("sdp");
                sdp = sdp.replaceAll("profile-level-id=42001f","profile-level-id=42e01f");
                SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER , sdp);
                Log.d("MATT","======  on receive answer message sdp :" + sdp);
                _events.onRemoteDescription(sessionDescription);
            }else if (type.equals("candidate")){

                String candidate = json.getJSONObject("data").getJSONObject("msg").getString("candidate");
                candidate = candidate.substring(2);
                IceCandidate iceCandidate = new IceCandidate("audio",0,candidate);
                Log.d("MATT","======  on receive candidate message sessionDescription :" + candidate);
                _events.onRemoteIceCandidate(iceCandidate);
            }else
            {
                Log.d("MATT","Receive other message..");
            }
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        Log.d("MQTT","deliveryComplete " + token.toString());
    }
}
