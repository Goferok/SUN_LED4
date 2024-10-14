package com.example.sun_led4

import android.content.Context
import android.net.wifi.WifiManager
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var seekBar2800K: SeekBar
    private lateinit var percent2800K: TextView
    private lateinit var seekBar4000K: SeekBar
    private lateinit var percent4000K: TextView
    private lateinit var seekBar5000K: SeekBar
    private lateinit var percent5000K: TextView
    private lateinit var seekBar5700K: SeekBar
    private lateinit var percent5700K: TextView
    private lateinit var temperatureTextView: TextView
    private lateinit var ssidEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var sendWiFiDataButton: Button
    private lateinit var findESPButton: Button
    private lateinit var espDeviceSpinner: Spinner
    private lateinit var deviceListLabel: TextView

    private var pwm2800K: Int = 0
    private var pwm4000K: Int = 0
    private var pwm5000K: Int = 0
    private var pwm5700K: Int = 0

    private val espList = mutableListOf<String>()
    private lateinit var espListAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация UI элементов
        seekBar2800K = findViewById(R.id.seekBar2800K)
        percent2800K = findViewById(R.id.percent2800K)
        seekBar4000K = findViewById(R.id.seekBar4000K)
        percent4000K = findViewById(R.id.percent4000K)
        seekBar5000K = findViewById(R.id.seekBar5000K)
        percent5000K = findViewById(R.id.percent5000K)
        seekBar5700K = findViewById(R.id.seekBar5700K)
        percent5700K = findViewById(R.id.percent5700K)
        temperatureTextView = findViewById(R.id.colorTemperatureText)
        ssidEditText = findViewById(R.id.ssidInput)
        passwordEditText = findViewById(R.id.passwordInput)
        sendWiFiDataButton = findViewById(R.id.connectWifiButton)
        findESPButton = findViewById(R.id.findESPButton)
        espDeviceSpinner = findViewById(R.id.deviceSpinner)
        deviceListLabel = findViewById(R.id.deviceListLabel)

        espListAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, espList)
        espListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        espDeviceSpinner.adapter = espListAdapter

        // Загрузка сохраненных устройств
        loadSavedDevices()

        // Настройка ползунков
        setupSeekBar(seekBar2800K, percent2800K) { pwm2800K = it }
        setupSeekBar(seekBar4000K, percent4000K) { pwm4000K = it }
        setupSeekBar(seekBar5000K, percent5000K) { pwm5000K = it }
        setupSeekBar(seekBar5700K, percent5700K) { pwm5700K = it }

        sendWiFiDataButton.setOnClickListener {
            val ssid = ssidEditText.text.toString()
            val password = passwordEditText.text.toString()
            SendWiFiDataTask().execute(ssid, password)
        }

        findESPButton.setOnClickListener {
            checkControllerConnection()
        }
    }

    // Функция для настройки ползунков
    private fun setupSeekBar(seekBar: SeekBar, percentView: TextView, onChange: (Int) -> Unit) {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                percentView.text = "$progress%"
                onChange(progress)
                updateTemperature()

                val scaledPwm2800K = (pwm2800K * 255) / 100
                val scaledPwm4000K = (pwm4000K * 255) / 100
                val scaledPwm5000K = (pwm5000K * 255) / 100
                val scaledPwm5700K = (pwm5700K * 255) / 100
                SendPWMTask().execute(scaledPwm2800K, scaledPwm4000K, scaledPwm5000K, scaledPwm5700K)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateTemperature() {
        val totalPWM = pwm2800K + pwm4000K + pwm5000K + pwm5700K
        if (totalPWM > 0) {
            val temperature = (pwm2800K * 2800 + pwm4000K * 4000 + pwm5000K * 5000 + pwm5700K * 5700) / totalPWM
            temperatureTextView.text = "Температура свечения: ${temperature}K"
        } else {
            temperatureTextView.text = "Температура свечения: N/A"
        }
    }

    // Загрузка сохраненных устройств
    private fun loadSavedDevices() {
        val sharedPreferences = getSharedPreferences("SavedDevices", MODE_PRIVATE)
        val savedDevices = sharedPreferences.getStringSet("devices", null)
        savedDevices?.forEach { device ->
            espList.add(device)
        }
        espListAdapter.notifyDataSetChanged()
    }

    // Метод для обновления Spinner с именем устройства, IP и статусом
    private fun updateSpinner(deviceName: String, ipAddress: String, status: String) {
        val deviceInfo = "$deviceName ($ipAddress) - $status"
        espList.add(deviceInfo)
        espListAdapter.notifyDataSetChanged()

        // Сохраняем устройство
        saveDevice(deviceInfo)
    }

    // Сохранение устройства в SharedPreferences
    private fun saveDevice(deviceInfo: String) {
        val sharedPreferences = getSharedPreferences("SavedDevices", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val savedDevices = sharedPreferences.getStringSet("devices", mutableSetOf())?.toMutableSet()
        savedDevices?.add(deviceInfo)
        editor.putStringSet("devices", savedDevices)
        editor.apply()
    }

    // Функция для поиска ESP32 или его подключения через точку доступа
    private fun checkControllerConnection() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val currentSSID = wifiManager.connectionInfo.ssid

        // Проверяем, подключены ли мы к сети контроллера ESP32
        if (currentSSID.contains("ESP32_AP")) {
            espList.add("192.168.4.1")  // IP адрес контроллера в режиме точки доступа
            espListAdapter.notifyDataSetChanged()
            Toast.makeText(this@MainActivity, "Подключено к контроллеру", Toast.LENGTH_SHORT).show()
        } else {
            scanLocalNetwork()
        }
    }

    // Функция для сканирования локальной сети на наличие устройств ESP32
    private fun scanLocalNetwork() {
        val executor = Executors.newFixedThreadPool(10)
        var newDevicesFound = false

        for (i in 1..254) {
            val ip = "192.168.0.$i"  // Предполагаем, что приложение в сети 192.168.0.x
            executor.execute {
                try {
                    val address = InetAddress.getByName(ip)
                    if (address.isReachable(100)) {
                        Log.d("SCAN", "Проверяем IP: $ip")
                        val url = URL("http://$ip:80")
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.setRequestProperty("Content-Type", "text/plain")
                        connection.doOutput = true

                        val outputStream: OutputStream = connection.outputStream
                        outputStream.write("SUNLED".toByteArray())
                        outputStream.flush()
                        outputStream.close()

                        val responseCode = connection.responseCode
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            val inputStream = connection.inputStream.bufferedReader().use { it.readText() }

                            if (inputStream.contains("SUNLED")) {
                                runOnUiThread {
                                    // Проверяем, есть ли уже это устройство в списке
                                    val deviceString = "$ip (Online)"
                                    if (!espList.contains(deviceString)) {
                                        espList.add(deviceString)
                                        espListAdapter.notifyDataSetChanged()
                                        newDevicesFound = true
                                        Toast.makeText(this@MainActivity, "Найдено новое устройство: $ip", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // После завершения поиска выводим сообщение, если новых устройств не найдено
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)  // Ожидаем завершения всех потоков

        runOnUiThread {
            if (!newDevicesFound) {
                Toast.makeText(this@MainActivity, "Новых устройств не найдено", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // Метод для показа диалогового окна ввода имени устройства
    private fun showNameInputDialog(ipAddress: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Введите имя устройства")

        val input = EditText(this)
        builder.setView(input)

        builder.setPositiveButton("OK") { dialog, _ ->
            val deviceName = input.text.toString()
            if (deviceName.isNotEmpty()) {
                updateSpinner(deviceName, ipAddress, "Online")
            } else {
                Toast.makeText(this, "Имя устройства не может быть пустым", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    // Задача для отправки данных SSID и пароля на ESP32
    private inner class SendWiFiDataTask : AsyncTask<String, Void, Void>() {
        override fun doInBackground(vararg params: String?): Void? {
            val ssid = params[0] ?: return null
            val password = params[1] ?: return null
            val url = URL("http://192.168.4.1:80/setWiFi")
            val postData = "ssid=$ssid&password=$password"

            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                doOutput = true
                val outputStream: OutputStream = outputStream
                outputStream.write(postData.toByteArray(Charsets.UTF_8))
                outputStream.flush()
                outputStream.close()

                val responseCode = responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    println("WiFi data sent successfully")
                } else {
                    println("Error sending WiFi data: $responseCode")
                }
            }
            return null
        }
    }

    // Задача для отправки данных PWM на выбранное устройство
    private inner class SendPWMTask : AsyncTask<Int, Void, Void>() {
        override fun doInBackground(vararg pwmValues: Int?): Void? {
            val pwm2800K = pwmValues[0] ?: return null
            val pwm4000K = pwmValues[1] ?: return null
            val pwm5000K = pwmValues[2] ?: return null
            val pwm5700K = pwmValues[3] ?: return null
            sendPWMValues(pwm2800K, pwm4000K, pwm5000K, pwm5700K)
            return null
        }

        private fun sendPWMValues(pwm2800K: Int, pwm4000K: Int, pwm5000K: Int, pwm5700K: Int) {
            // Получаем выбранное устройство из Spinner
            val selectedDevice = espDeviceSpinner.selectedItem.toString()
            val ipAddress = selectedDevice.substringAfter("(").substringBefore(")")
            val url = URL("http://$ipAddress:80/setPWM")
            val postData = "2800K=$pwm2800K&4000K=$pwm4000K&5000K=$pwm5000K&5700K=$pwm5700K"

            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                doOutput = true
                val outputStream: OutputStream = outputStream
                outputStream.write(postData.toByteArray(Charsets.UTF_8))
                outputStream.flush()
                outputStream.close()

                val responseCode = responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    println("PWM values sent successfully to $selectedDevice")
                } else {
                    println("Error sending PWM values to $selectedDevice: $responseCode")
                }
            }
        }
    }
}

