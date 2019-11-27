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

        button = findViewById(R.id.button)
        button!!.text = "Start"
        button!!.setOnClickListener {
            if (pinger != null) {
                Stop()
                return@setOnClickListener
            }

            pinger = Pinger()
            pinger?.setOnPingListener(object : Pinger.OnPingListener {
                override fun OnTimeout(pingInfo: PingInfo, sequence: Int) {
                    addToLog("#$sequence: Timeout!")
                    if (sequence >= 10)
                        Stop()
                }

                override fun OnReplyReceived(pingInfo: PingInfo, sequence: Int, content: String) {
                    addToLog("#$sequence: Reply from ${pingInfo.RemoteIp}: bytes=${pingInfo.Size} time=$content TTL=${pingInfo.Ttl}")
                    if (sequence >= 100)
                        Stop()
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
                        Stop()
                }

            })
//        pinger?.Ping("192.168.3.100","AUTOBEEP")
            pinger?.Ping("192.168.3.100", "AUTOPOS(0)")
//        pinger?.Ping("192.168.3.100","AUTOPOS(1001)")
//        pinger?.Ping("192.168.3.100","AUTORDPLUPRICE(1001)")
//        pinger?.Ping("192.168.3.100","AUTOWRPLUPRICE(1001 1000)")
            button!!.text = "Stop"
        }
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
