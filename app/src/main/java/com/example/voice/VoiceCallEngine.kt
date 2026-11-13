package com.example.voice

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.data.model.IceCandidateRecord
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

interface VoiceCallEngine {
  fun startAsCaller(
    onOfferCreated: (String) -> Unit,
    onIceCandidate: (sdpMid: String, sdpMLineIndex: Int, candidate: String) -> Unit,
    onConnected: () -> Unit,
    onDisconnected: (String) -> Unit,
  )

  fun startAsCallee(
    remoteOfferSdp: String,
    onAnswerCreated: (String) -> Unit,
    onIceCandidate: (sdpMid: String, sdpMLineIndex: Int, candidate: String) -> Unit,
    onConnected: () -> Unit,
    onDisconnected: (String) -> Unit,
  )

  fun setRemoteAnswer(answerSdp: String)
  fun addRemoteIceCandidate(candidate: IceCandidateRecord)
  fun setMuted(muted: Boolean)
  fun setSpeakerEnabled(enabled: Boolean)
  fun stop()
}

class WebRtcVoiceCallEngine(private val context: Context) : VoiceCallEngine {

  private val appContext = context.applicationContext
  private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  private var factory: PeerConnectionFactory? = null
  private var peerConnection: PeerConnection? = null
  private var audioSource: AudioSource? = null
  private var localAudioTrack: AudioTrack? = null
  private var audioDeviceModule: JavaAudioDeviceModule? = null
  private var remoteDescriptionSet = false
  private val pendingCandidates = mutableListOf<IceCandidate>()

  private fun ensureInitialized() {
    if (factory != null) return
    PeerConnectionFactory.initialize(
      PeerConnectionFactory.InitializationOptions.builder(appContext)
        .setEnableInternalTracer(false)
        .createInitializationOptions()
    )
    val adm = JavaAudioDeviceModule.builder(appContext).createAudioDeviceModule()
    audioDeviceModule = adm
    factory =
      PeerConnectionFactory.builder()
        .setAudioDeviceModule(adm)
        .createPeerConnectionFactory()
  }

  private fun createPeerConnection(
    onIceCandidate: (String, Int, String) -> Unit,
    onConnected: () -> Unit,
    onDisconnected: (String) -> Unit,
  ): PeerConnection? {
    ensureInitialized()
    val iceServers =
      listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
      )
    val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
      sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
      continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
    }

    val pc =
      factory?.createPeerConnection(
        rtcConfig,
        object : PeerConnection.Observer {
          override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
          override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            Log.d(TAG, "ICE state: $state")
            when (state) {
              PeerConnection.IceConnectionState.CONNECTED,
              PeerConnection.IceConnectionState.COMPLETED -> onConnected()
              PeerConnection.IceConnectionState.FAILED -> onDisconnected("Network connection failed")
              PeerConnection.IceConnectionState.DISCONNECTED -> onDisconnected("Connection interrupted")
              else -> {}
            }
          }

          override fun onIceConnectionReceivingChange(receiving: Boolean) {}
          override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
          override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate != null) {
              onIceCandidate(candidate.sdpMid ?: "0", candidate.sdpMLineIndex, candidate.sdp)
            }
          }

          override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
          override fun onAddStream(stream: MediaStream?) {}
          override fun onRemoveStream(stream: MediaStream?) {}
          override fun onDataChannel(dc: DataChannel?) {}
          override fun onRenegotiationNeeded() {}
          override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        },
      )

    if (pc != null) {
      val audioConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
      }
      audioSource = factory?.createAudioSource(audioConstraints)
      localAudioTrack = factory?.createAudioTrack("ARDAMSa0", audioSource)?.apply {
        setEnabled(true)
      }
      localAudioTrack?.let { track ->
        pc.addTrack(track, listOf("ARDAMS"))
      }
      audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
      audioManager.isSpeakerphoneOn = false
    }
    return pc
  }

  override fun startAsCaller(
    onOfferCreated: (String) -> Unit,
    onIceCandidate: (String, Int, String) -> Unit,
    onConnected: () -> Unit,
    onDisconnected: (String) -> Unit,
  ) {
    stop()
    remoteDescriptionSet = false
    val pc = createPeerConnection(onIceCandidate, onConnected, onDisconnected)
    peerConnection = pc
    if (pc == null) {
      onDisconnected("Unable to initialize audio session")
      return
    }
    val constraints = MediaConstraints().apply {
      mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
      mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }
    pc.createOffer(
      object : SimpleSdpObserver() {
        override fun onCreateSuccess(sdp: SessionDescription?) {
          if (sdp == null) return
          pc.setLocalDescription(
            object : SimpleSdpObserver() {
              override fun onSetSuccess() {
                onOfferCreated(sdp.description)
              }
            },
            sdp,
          )
        }
      },
      constraints,
    )
  }

  override fun startAsCallee(
    remoteOfferSdp: String,
    onAnswerCreated: (String) -> Unit,
    onIceCandidate: (String, Int, String) -> Unit,
    onConnected: () -> Unit,
    onDisconnected: (String) -> Unit,
  ) {
    stop()
    remoteDescriptionSet = false
    val pc = createPeerConnection(onIceCandidate, onConnected, onDisconnected)
    peerConnection = pc
    if (pc == null) {
      onDisconnected("Unable to initialize audio session")
      return
    }
    val remoteOffer = SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp)
    pc.setRemoteDescription(
      object : SimpleSdpObserver() {
        override fun onSetSuccess() {
          remoteDescriptionSet = true
          flushPendingCandidates(pc)
          val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
          }
          pc.createAnswer(
            object : SimpleSdpObserver() {
              override fun onCreateSuccess(answer: SessionDescription?) {
                if (answer == null) return
                pc.setLocalDescription(
                  object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                      onAnswerCreated(answer.description)
                    }
                  },
                  answer,
                )
              }
            },
            constraints,
          )
        }
      },
      remoteOffer,
    )
  }

  override fun setRemoteAnswer(answerSdp: String) {
    if (answerSdp.isBlank() || remoteDescriptionSet) return
    val pc = peerConnection ?: return
    val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
    pc.setRemoteDescription(
      object : SimpleSdpObserver() {
        override fun onSetSuccess() {
          remoteDescriptionSet = true
          flushPendingCandidates(pc)
        }
      },
      answer,
    )
  }

  override fun addRemoteIceCandidate(candidate: IceCandidateRecord) {
    val ice = IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate)
    val pc = peerConnection
    if (pc != null && remoteDescriptionSet) {
      pc.addIceCandidate(ice)
    } else {
      pendingCandidates.add(ice)
    }
  }

  private fun flushPendingCandidates(pc: PeerConnection) {
    pendingCandidates.forEach { pc.addIceCandidate(it) }
    pendingCandidates.clear()
  }

  override fun setMuted(muted: Boolean) {
    localAudioTrack?.setEnabled(!muted)
    audioManager.isMicrophoneMute = muted
  }

  override fun setSpeakerEnabled(enabled: Boolean) {
    audioManager.isSpeakerphoneOn = enabled
  }

  override fun stop() {
    runCatching {
      pendingCandidates.clear()
      remoteDescriptionSet = false
      localAudioTrack?.setEnabled(false)
      localAudioTrack?.dispose()
      localAudioTrack = null
      audioSource?.dispose()
      audioSource = null
      peerConnection?.close()
      peerConnection?.dispose()
      peerConnection = null
      audioDeviceModule?.release()
      audioDeviceModule = null
      audioManager.isMicrophoneMute = false
      audioManager.isSpeakerphoneOn = false
      audioManager.mode = AudioManager.MODE_NORMAL
    }.onFailure {
      Log.w(TAG, "Error stopping WebRTC engine: ${it.message}")
    }
  }

  private open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {
      Log.e(TAG, "SDP create failure: $error")
    }

    override fun onSetFailure(error: String?) {
      Log.e(TAG, "SDP set failure: $error")
    }
  }

  private companion object {
    const val TAG = "WebRtcVoiceEngine"
  }
}
