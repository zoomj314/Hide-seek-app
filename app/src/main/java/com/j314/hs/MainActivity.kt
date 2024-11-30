package com.j314.hs

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.wifi.WifiManager
import android.os.*
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import kotlin.math.floor
import kotlin.math.round


class MainActivity : AppCompatActivity() {

    private val connectionTestSend = "hi"
    private val connectionTestReply = "received. let's go!"
    private val timerIdle = "00:00"
    private val serverPort = 6875
    private val maxPings = 15
    var ip: String? = null
    var connected = false
    val buzzTimes = listOf(59, 30, 10, 5)
    var hider = false
    var serverStarted = false
    var hiding = false
    var locked = false
    var pings = 0
    lateinit var client: Socket
    lateinit var mediaPlayer: MediaPlayer

    var hideTime = 10
    var findTime = 10


    lateinit var connectionStatusText: TextView
    lateinit var gameStatusText: TextView
    lateinit var timerView: TextView
    lateinit var startServerButton: Button
    lateinit var pingButton: Button
    lateinit var startButton: Button
    lateinit var stopButton: Button
    lateinit var readyButton: Button
    lateinit var quietButton: Button
    lateinit var ipInput: EditText
    lateinit var hideTimeMinInput: EditText
    lateinit var hideTimeSecInput: EditText
    lateinit var findTimeMinInput: EditText
    lateinit var findTimeSecInput: EditText
    lateinit var connectButton: Button
    lateinit var gameContainer: ConstraintLayout
    lateinit var gameOptions: ConstraintLayout
    lateinit var vibrator: Vibrator
    lateinit var timer: CountDownTimer
    lateinit var server: ServerSocket


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Set variables

        // Get interface items
        ipInput = findViewById<EditText>(R.id.ipInput)
        hideTimeMinInput = findViewById<EditText>(R.id.hideTimeMin)
        hideTimeSecInput = findViewById<EditText>(R.id.hideTimeSec)
        findTimeMinInput = findViewById<EditText>(R.id.findTimeMin)
        findTimeSecInput = findViewById<EditText>(R.id.findTimeSec)
        connectButton = findViewById<Button>(R.id.connectButton)
        startButton = findViewById<Button>(R.id.startButton)
        stopButton = findViewById<Button>(R.id.stopButton)
        pingButton = findViewById<Button>(R.id.pingButton)
        readyButton = findViewById<Button>(R.id.readyButton)
        quietButton = findViewById<Button>(R.id.quietButton)
        startServerButton = findViewById<Button>(R.id.startServerButton)
        connectionStatusText = findViewById<TextView>(R.id.connectionStatus)
        gameStatusText = findViewById<TextView>(R.id.gameStatus)
        timerView = findViewById<TextView>(R.id.timer)
        gameContainer = findViewById<ConstraintLayout>(R.id.gameContainer)
        gameOptions = findViewById<ConstraintLayout>(R.id.gameOptions)
        val lockButton = findViewById<ImageButton>(R.id.lockButton)


        // Get vibrator
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        val audioManager = applicationContext.getSystemService(AUDIO_SERVICE) as AudioManager


        // Get ip address
        ip = getLocalIpAddress(this)

        if (ip != null) {
            ipInput.setText("${ip!!.split(".")[0]}.${ip!!.split(".")[1]}.${ip!!.split(".")[2]}.")
        }

        // Set on click listeners
        connectButton.setOnClickListener{
            if (ipInput.text.matches(Regex("""\d+.\d+.\d+.\d+"""))) {
                println("connecting to ${ipInput.text.toString()}")
                CoroutineScope(Dispatchers.IO).launch {
                    connectToServer(ipInput.text.toString())
                }
            } else{
                Toast.makeText(this, "Invalid ip address", Toast.LENGTH_SHORT).show()
            }
        }
        startServerButton.setOnClickListener {
            if (!serverStarted) {
                CoroutineScope(Dispatchers.IO).launch {
                    startServer()
                }
                startServerButton.text = "Stop server"
                ipInput.visibility = View.GONE
                connectButton.visibility = View.GONE
            } else {
                stopServer()
                startServerButton.text = "Start server"
                ipInput.visibility = View.VISIBLE
                connectButton.visibility = View.VISIBLE
            }
        }
        pingButton.setOnClickListener{
            CoroutineScope(Dispatchers.IO).launch{
                sendMessage("ping")
            }
            if (hider){
                pings++
            }
            if (pings >= maxPings){
                Toast.makeText(this, "Stop pinging!", Toast.LENGTH_LONG).show()
            }
            if (pings >= maxPings){
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_VIBRATE)
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_VIBRATE)
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_VIBRATE)
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_VIBRATE)
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_VIBRATE)
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_VIBRATE)
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_VIBRATE)
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_VIBRATE)
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_VIBRATE)
                buzz(1000, true)
            }
        }
        for (i in listOf(hideTimeSecInput, hideTimeMinInput, findTimeMinInput, findTimeSecInput)) {
            i.setSelectAllOnFocus(true)
        }
        startButton.setOnClickListener {
            hideTime = (hideTimeMinInput.text.toString().toInt()*60)+hideTimeSecInput.text.toString().toInt()
            findTime = (findTimeMinInput.text.toString().toInt()*60)+findTimeSecInput.text.toString().toInt()
            //TODO finish setting variables based on min/sec
            hider = false
            CoroutineScope(Dispatchers.IO).launch {
                sendMessage("start $hideTime $findTime")
            }
            startHide()
        }
        readyButton.setOnClickListener{
            if (!locked) {
                if (hiding) {
                    timer.cancel()
                    startFind()
                    CoroutineScope(Dispatchers.IO).launch {
                        sendMessage("find")
                    }
                } else {
                    endGame(false)
                }
            }
        }
        stopButton.setOnClickListener {
            if (!locked) {
                endGame(true)
                CoroutineScope(Dispatchers.IO).launch {
                    sendMessage("stop")
                }
            }
        }
        quietButton.setOnClickListener {
            mediaPlayer.stop()
            quietButton.visibility = View.GONE
        }
        lockButton.setOnClickListener {
            if (locked){
                locked = false
                lockButton.setImageDrawable(resources.getDrawable(R.drawable.ic_baseline_lock_24))
            } else {
                locked = true
                lockButton.setImageDrawable(resources.getDrawable(R.drawable.ic_baseline_lock_24_dark))
            }
        }

        connectionStatusText.text = "ready: $ip"
    }



    // Function that listens for messages
    private suspend fun listen(scanner: Scanner){
        var rcvd: String
        while (scanner.hasNextLine()) {
            rcvd = scanner.nextLine()
            println(rcvd)
            if (BuildConfig.DEBUG) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplicationContext(), rcvd, Toast.LENGTH_SHORT).show()
                }
            }
            withContext(Dispatchers.Main) {
                when (rcvd.split(" ")[0]) {
                    "start" -> {
                        hider = true
                        pings = 0
                        hideTime = rcvd.split(" ")[1].toInt()
                        findTime = rcvd.split(" ")[2].toInt()
                        startHide()
                    }
                    "stop" -> endGame(true, false)
                    "finish" -> endGame(false)
                    "ping" -> buzz(500)
                    "find" -> {
                        timer.cancel()
                        startFind()
                    }
                }
            }
        }
    }


    private fun startHide(){
        hiding = true
        startButton.visibility = View.GONE
        stopButton.visibility = View.VISIBLE
        gameOptions.visibility = View.GONE

        window.decorView.apply {
            systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
        }

        if (hider){
            gameStatusText.text = "Hide"
            readyButton.visibility = View.VISIBLE
        } else {
            gameStatusText.text = "Wait"
        }
        val hideTimeMils = hideTime*1000
        timerView.text = hideTime.toString()
        timerView.setTextColor(resources.getColor(R.color.orange))
        timer = object: CountDownTimer(hideTimeMils.toLong(), 1000) {
            override fun onTick(remaining: Long) {
                val remainingSecs = round(((remaining/1000.00))).toInt()
                timerView.text = "${floor(remainingSecs/60.0).toInt().toString().padStart(2,'0')}:${(remainingSecs%60).toString().padStart(2,'0')}"
                if (round(((remaining/1000.00))).toInt() in buzzTimes){
                    buzz(300)
                }
            }
            override fun onFinish() {
                startFind()
            }
        }
        timer.start()

    }

    fun startFind(){
        hiding=false
        if (hider){
            gameStatusText.text = "Wait"
            timerView.setTextColor(resources.getColor(R.color.orange))
            readyButton.visibility = View.GONE
        } else {
            gameStatusText.text = "Find"
            timerView.setTextColor(resources.getColor(R.color.green))
        }
        val findTimeMils = findTime*1000

        window.decorView.apply {
            systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
        }

        buzz(500, !hider) // Play tone if not hider

        timerView.text = findTime.toString()

        timer = object: CountDownTimer(findTimeMils.toLong(), 1000) {
            override fun onTick(remaining: Long) {
                val remainingSecs = round(((remaining/1000.00))).toInt()
                timerView.text = "${floor(remainingSecs/60.0).toInt().toString().padStart(2,'0')}:${(remainingSecs%60).toString().padStart(2,'0')}"
            }
            override fun onFinish() {
                endFind()
            }
        }
        timer.start()
    }

    fun endFind(){
        timerView.text = timerIdle
        if (hider){
            timerView.setTextColor(resources.getColor(R.color.green))
            gameStatusText.text = "Wait"
        }else{
            timerView.setTextColor(resources.getColor(R.color.red))
            buzz(1000, true)
            gameStatusText.text = "Go to start"
            readyButton.visibility = View.VISIBLE
        }
    }

    private fun endGame(abort: Boolean, sendAbort: Boolean = true){
        hiding=false
        readyButton.visibility = View.GONE
        startButton.visibility = View.VISIBLE
        stopButton.visibility = View.GONE
        timerView.setTextColor(resources.getColor(R.color.black))
        gameOptions.visibility = View.VISIBLE
        if (abort) {
            gameStatusText.text = "Ready"
            timerView.text = timerIdle
            if (sendAbort) {
                CoroutineScope(Dispatchers.IO).launch {
                    sendMessage("stop")
                }
            }
            timer.cancel()
        } else {
            if (hider){
                gameStatusText.text = "You can come out"
                buzz(1000)
            } else {
                gameStatusText.text = "Ready"
                CoroutineScope(Dispatchers.IO).launch {
                    sendMessage("finish")
                }
                buzz(500)
            }
        }
    }


    // Function to vibrate
    fun buzz(milliseconds: Long, sound: Boolean = false){
        println("buzzing")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            //deprecated in API 26
            vibrator.vibrate(milliseconds)
        }
        if (sound) {
            mediaPlayer = MediaPlayer.create(this, R.raw.horn)
            mediaPlayer.start()
            quietButton.visibility = View.VISIBLE
            mediaPlayer.setOnCompletionListener { quietButton.visibility = View.GONE }
        }
    }

    // Function to run when connected
    private suspend fun onConnected(){
        connected = true
        withContext(Dispatchers.Main) {
            pingButton.isEnabled = true
            startServerButton.visibility = View.GONE
            connectButton.visibility = View.GONE
            ipInput.visibility = View.GONE
            gameContainer.visibility = View.VISIBLE
            gameOptions.visibility = View.VISIBLE
        }
    }

    // Function to run when disconnected
    private fun onDisconnect(){
        connected = false
        serverStarted = false
        pingButton.isEnabled = false
        connectButton.visibility = View.VISIBLE
        startServerButton.visibility = View.VISIBLE
        ipInput.visibility = View.VISIBLE
        gameContainer.visibility = View.GONE
    }




    // Function to start a server on the local device
    private suspend fun startServer() {
        serverStarted = true
        server = ServerSocket(serverPort)
        println("Server running on port ${server.localPort}")
        withContext(Dispatchers.Main){
            connectionStatusText.text = "listening on $ip"
            connectionStatusText.setBackgroundColor(resources.getColor(R.color.white))
        }
        try {
            client = server.accept()
            println("Client connected : ${client.inetAddress.hostAddress}")
            val scanner = Scanner(client.inputStream)


            // Test connection
            val rcvd = scanner.nextLine()
            println("recieved $rcvd")
            // Check if message equals expected message, then reply
            if (rcvd == connectionTestSend){
                client.outputStream.write((connectionTestReply+"\n").toByteArray())
                println("sent $connectionTestReply")
                // Update interface
                withContext(Dispatchers.Main) {
                    connectionStatusText.text = "${client.inetAddress.hostAddress} connected"
                    connectionStatusText.setBackgroundColor(resources.getColor(R.color.green))
                    gameStatusText.text = "Ready"
                }
                onConnected()

                // Listen for messages
                listen(scanner)

                // Do this once disconnected
                println("disconnected")
                withContext(Dispatchers.Main) {
                    connectionStatusText.text = "client disconnected"
                    connectionStatusText.setBackgroundColor(resources.getColor(R.color.red))
                    gameStatusText.text = "Not connected"
                }
                server.close()
                withContext(Dispatchers.Main) {
                    startServerButton.isEnabled = true
                }
                withContext(Dispatchers.Main) {
                    onDisconnect()
                }
            } else {
                withContext(Dispatchers.Main) {
                    connectionStatusText.text = "error"
                    connectionStatusText.setBackgroundColor(resources.getColor(R.color.red))
                }
            }
        } catch (e: Exception) {
            println("didn't connect")
            withContext(Dispatchers.Main){
                connectionStatusText.text = "not connected"
                connectionStatusText.setBackgroundColor(resources.getColor(R.color.red))
            }
        }
    }

    private fun stopServer(){
        server.close()
        onDisconnect()
    }


    // Function to connect to a server
    private suspend fun connectToServer(serverIpAddress: String){
        withContext(Dispatchers.Main) {
            connectionStatusText.text = "connecting to $serverIpAddress"
        }
        try {
            client = Socket(serverIpAddress, serverPort)
            println("connected to $serverIpAddress")

            val scanner = Scanner(client.inputStream)

            // Test connection
            client.outputStream.write((connectionTestSend + "\n").toByteArray())
            println("sent $connectionTestSend")
            val rcvd = scanner.nextLine()
            println("recieved $rcvd")
            // Check if reply matches expected reply
            if (rcvd == connectionTestReply) {
                // Update interface
                withContext(Dispatchers.Main) {
                    connectionStatusText.text = "connected to $serverIpAddress"
                    connectionStatusText.setBackgroundColor(resources.getColor(R.color.green))
                    gameStatusText.text = "Ready"
                }
                onConnected()
            } else {
                withContext(Dispatchers.Main) {
                    connectionStatusText.text = "error connecting to $serverIpAddress"
                    connectionStatusText.setBackgroundColor(resources.getColor(R.color.red))
                }
            }


            // Listen for messages
            listen(scanner)

            // Do this once disconnected
            println("server disconnected")
            withContext(Dispatchers.Main) {
                connectionStatusText.text = "server disconnected"
                connectionStatusText.setBackgroundColor(resources.getColor(R.color.red))
                gameStatusText.text = "Not connected"
            }

            client.close()

            withContext(Dispatchers.Main) {
                onDisconnect()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                connectionStatusText.text = "error connecting"
                connectionStatusText.setBackgroundColor(resources.getColor(R.color.red))
            }
        }
    }



    // Function to send messages through connection
    suspend fun sendMessage(message: String){
        if (connected) {
            client.outputStream.write((message + "\n").toByteArray())
        } else {
            println("not connected")
        }
    }



    private fun getLocalIpAddress(context: Context): String? {
        try {

            val wifiManager: WifiManager = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            return ipToString(wifiManager.connectionInfo.ipAddress)
        } catch (ex: Exception) {
            Log.e("IP Address", ex.toString())
        }

        return null
    }

    private fun ipToString(i: Int): String {
        return (i and 0xFF).toString() + "." +
                (i shr 8 and 0xFF) + "." +
                (i shr 16 and 0xFF) + "." +
                (i shr 24 and 0xFF)
    }

}

