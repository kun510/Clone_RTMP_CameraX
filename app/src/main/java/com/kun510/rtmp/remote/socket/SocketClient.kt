package com.kun510.rtmp.remote.socket

import android.util.Log
import com.kun510.rtmp.local.MySharedPreference
import com.kun510.rtmp.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.net.URISyntaxException

enum class SocketState {
    Connecting, Connected
}

object SocketClient  {

    private val TAG = "MainService-socket"

    private var socket: WebSocketClient? = null

    private val job = CoroutineScope(Dispatchers.IO)
    private var listener: Listener? = null
    private var isReconnectingStopped = false

    fun initialize(listener: Listener,preference: MySharedPreference) {
        Log.d(TAG, "initialize: ${Constants.getSocketUrl(preference.getToken()!!)}")
        CoroutineScope(Dispatchers.IO).launch {

            try {
                socket = object :
                    WebSocketClient(URI(Constants.getSocketUrl(preference.getToken()!!))) {
                    override fun onOpen(handshakedata: ServerHandshake?) {
                        listener.onConnectionStateChanged(SocketState.Connected)
                        Log.d(TAG, "onOpen: connected")
                    }

                    override fun onMessage(message: String?) {
                        message?.let {
                            listener.onNewMessageReceived(it)
                        }
                    }

                    override fun onClose(code: Int, reason: String?, remote: Boolean) {
                        listener.onConnectionStateChanged(SocketState.Connecting)
                        Log.d(TAG, "onClose: closed")
                        job.launch {
                            delay(5000)
                            if (!isReconnectingStopped){
                                initialize(listener,preference)
                            }
                        }
                    }

                    override fun onError(ex: java.lang.Exception?) {
                        Log.d(TAG, "onError: error $ex")
//                        job.launch {
//                            delay(5000)
//                            initialize(listener)
//                        }
                        listener.onConnectionStateChanged(SocketState.Connecting)
                    }

                }
                socket?.connect()
            } catch (e: URISyntaxException) {
                listener.onConnectionStateChanged(SocketState.Connecting)

            } catch (e: Exception) {
                listener.onConnectionStateChanged(SocketState.Connecting)
            }
        }


    }

    fun unregisterClients() {
        listener = null
    }


    fun closeSocket() {
        try {
            socket?.close()
            isReconnectingStopped = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    interface Listener {
        fun onConnectionStateChanged(state: SocketState)
        fun onNewMessageReceived(message: String)
    }
}