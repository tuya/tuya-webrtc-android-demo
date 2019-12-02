/*
 *  Copyright 2013 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.List;

/**
 * AppRTCClient is the interface representing an AppRTC client.
 */
public interface AppRTCClient {
	/**
	 * Struct holding the connection parameters of an AppRTC room.
	 */
	class RoomConnectionParameters {
		public final String roomUrl;
		public final String roomId;
		public final boolean loopback;
		public final String urlParameters;
		public final String clientId ;
		public final String secret ;
		public final String deviceid ;
		public final String localKey ;
		public final String AuthCode ;


		public RoomConnectionParameters(String roomUrl, String roomId, boolean loopback, String urlParameters,String clientid,String secret,String deviceid,String localkey,String authCode) {
			this.roomUrl = roomUrl;
			this.roomId = roomId;
			this.loopback = loopback;
			this.urlParameters = urlParameters;
			this.clientId = clientid;
			this.secret = secret ;
			this.deviceid = deviceid ;
			this.localKey = localkey ;
			this.AuthCode = authCode ;
		}
		public RoomConnectionParameters(String roomUrl, String roomId, boolean loopback, String urlParameters)
		{
			this(roomUrl,roomId,loopback,urlParameters,null,null,null,null,null);
		}
		public RoomConnectionParameters(String roomUrl, String roomId, boolean loopback)
		{
			this(roomUrl, roomId, loopback, null /* urlParameters */,null,null,null,null,null);
		}
		public RoomConnectionParameters(String roomUrl, String roomId, boolean loopback,String clientid,String secret,String did,String locakey,String authCode) {
			this(roomUrl, roomId, loopback, null /* urlParameters */,clientid,secret,did,locakey,authCode);
		}
	}

	/**
	 * Asynchronously connect to an AppRTC room URL using supplied connection
	 * parameters. Once connection is established onConnectedToRoom()
	 * callback with room parameters is invoked.
	 */
	void connectToRoom(RoomConnectionParameters connectionParameters);

	/**
	 * Send offer SDP to the other participant.
	 */
	void sendOfferSdp(final SessionDescription sdp);

	/**
	 * Send answer SDP to the other participant.
	 */
	void sendAnswerSdp(final SessionDescription sdp);

	/**
	 * Send Ice candidate to the other participant.
	 */
	void sendLocalIceCandidate(final IceCandidate candidate);

	/**
	 * Send removed ICE candidates to the other participant.
	 */
	void sendLocalIceCandidateRemovals(final IceCandidate[] candidates);

	/**
	 * Disconnect from room.
	 */
	void disconnectFromRoom();

	/**
	 * Struct holding the signaling parameters of an AppRTC room.
	 */
	class SignalingParameters {
		public  List<PeerConnection.IceServer> iceServers;
		public  boolean initiator;
		public  String clientId;
		public  String wssUrl;
		public  String wssPostUrl;
		public  SessionDescription offerSdp;
		public  List<IceCandidate> iceCandidates;

		public SignalingParameters()
		{

		}
		public SignalingParameters(List<PeerConnection.IceServer> iceServers, boolean initiator,
		                           String clientId, String wssUrl, String wssPostUrl, SessionDescription offerSdp,
		                           List<IceCandidate> iceCandidates) {
			this.iceServers = iceServers;
			this.initiator = initiator;
			this.clientId = clientId;
			this.wssUrl = wssUrl;
			this.wssPostUrl = wssPostUrl;
			this.offerSdp = offerSdp;
			this.iceCandidates = iceCandidates;
		}
	}

	/**
	 * Callback interface for messages delivered on signaling channel.
	 *
	 * <p>Methods are guaranteed to be invoked on the UI thread of |activity|.
	 */
	interface SignalingEvents {
		/**
		 * Callback fired once the room's signaling parameters
		 * SignalingParameters are extracted.
		 */
		void onConnectedToRoom(final SignalingParameters params);

		/**
		 * Callback fired once remote SDP is received.
		 */
		void onRemoteDescription(final SessionDescription sdp);

		/**
		 * Callback fired once remote Ice candidate is received.
		 */
		void onRemoteIceCandidate(final IceCandidate candidate);

		/**
		 * Callback fired once remote Ice candidate removals are received.
		 */
		void onRemoteIceCandidatesRemoved(final IceCandidate[] candidates);

		/**
		 * Callback fired once channel is closed.
		 */
		void onChannelClose();

		/**
		 * Callback fired once channel error happened.
		 */
		void onChannelError(final String description);
	}
}
