package com.example.myapplication

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.TextView
import com.tony.icmplib.PingInfo
import com.tony.icmplib.Pinger
import com.tony.icmplib.SCLog

class MainActivity : AppCompatActivity() {

    private var pinger: Pinger? = null
    private var button: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startPing()
        button = findViewById(R.id.button)
        button!!.text = "Start"
    }

    private fun startPing() {
        pinger = Pinger()
        pinger?.add2WhiteOrderList(arrayListOf("xxxx"))
        pinger?.setOnPingListener(object : Pinger.OnPingListener {
            override fun OnTimeout(pingInfo: PingInfo, sequence: Int) {
                addToLog("#$sequence: Timeout!")
                if (sequence >= 10)
                    restartAgain()
            }

            override fun OnReplyReceived(pingInfo: PingInfo, sequence: Int, content: String) {
                addToLog("#$sequence: Reply from ${pingInfo.RemoteIp}: bytes=${pingInfo.Size} time=$content")
            }

            override fun OnSendError(pingInfo: PingInfo, sequence: Int) {
                addToLog("#$sequence: PING error!")
            }

            override fun OnStop(pingInfo: PingInfo) {
                addToLog("Ping complete!")
            }

            override fun OnStart(pingInfo: PingInfo) {
                addToLog("Pinging ${pingInfo.ReverseDns} [${pingInfo.RemoteIp}] with ${pingInfo.Size} bytes of data:")
            }

            override fun OnException(pingInfo: PingInfo, e: Exception, isFatal: Boolean) {
                addToLog("$e")
                if (isFatal)
                    restartAgain()

            }

        })
        pinger?.Ping("192.168.0.1", "xxxxx")


    }

    private fun restartAgain() {
        Stop()
        startPing()
    }


    fun addToLog(content: String) {
        SCLog.i(content)
    }

    fun Stop() {
        pinger?.StopAll()
        pinger = null
        runOnUiThread {
            button!!.text = "Start"
        }
    }
}
