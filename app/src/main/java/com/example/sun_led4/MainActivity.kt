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
import android.os.Handler
import android.os.Looper

class MainActivity : AppCompatActivity() {

    private var pwmUpdateHandler = Handler(Looper.getMainLooper())
    private var pwmUpdateRunnable: Runnable? = null
    private val pwmUpdateDelay: Long = 100  // Задержка 100 мс между отправками
    private lateinit var seekBar2800K: SeekBar
    private lateinit var percent2800K: TextView
    private lateinit var seekBar4000K: SeekBar
    private lateinit var percent4000K: TextView
    private lateinit var seekBar5000K: SeekBar
    private lateinit var percent5000K: TextView
    private lateinit var seekBar5700K: SeekBar
    private lateinit var percent5700K: TextView
    private lateinit var temperatureSeekBar: SeekBar
    private lateinit var brightnessSeekBar: SeekBar
    private lateinit var temperatureTextView: TextView
    private lateinit var brightnessTextView: TextView
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
    private var temperatureValue: Int = 2800  // Значение температуры
    private var brightnessValue: Int = 100    // Значение яркости

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
        temperatureSeekBar = findViewById(R.id.temperatureSeekBar)
        brightnessSeekBar = findViewById(R.id.brightnessSeekBar)
        temperatureTextView = findViewById(R.id.colorTemperatureText)
        brightnessTextView = findViewById(R.id.brightnessTextView)
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

        // Настройка ползунков для каждого канала
        setupSeekBar(seekBar2800K, percent2800K) { pwm2800K = it }
        setupSeekBar(seekBar4000K, percent4000K) { pwm4000K = it }
        setupSeekBar(seekBar5000K, percent5000K) { pwm5000K = it }
        setupSeekBar(seekBar5700K, percent5700K) { pwm5700K = it }

        // Настройка ползунков температуры и яркости
        setupTemperatureSeekBar()
        setupBrightnessSeekBar()

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
                // Отображаем текущее значение в текстовом поле, но не отправляем на контроллер
                percentView.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Можно сделать что-то при начале перетаскивания (если нужно)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Когда пользователь отпустил ползунок, обновляем значение PWM
                val progress = seekBar?.progress ?: 0
                onChange(progress)
                updateTemperature()  // Пересчёт температуры
                sendPWMValuesWithDelay()  // Отправляем PWM с задержкой, чтобы избежать частых обновлений
            }
        })
    }

    // Функция для обновления отображения температуры
    private fun updateTemperature() {
        val totalPWM = pwm2800K + pwm4000K + pwm5000K + pwm5700K
        if (totalPWM > 0) {
            val temperature = (pwm2800K * 2800 + pwm4000K * 4000 + pwm5000K * 5000 + pwm5700K * 5700) / totalPWM
            temperatureTextView.text = "Температура свечения: ${temperature}K"
        } else {
            temperatureTextView.text = "Температура свечения: N/A"
        }
    }
    // Настройка ползунка температуры
    private fun setupTemperatureSeekBar() {
        temperatureSeekBar.max = 2900 // Разница между 2800 и 5700K
        temperatureSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                temperatureValue = 2800 + progress
                temperatureTextView.text = "Температура: $temperatureValue K"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Можно сделать что-то при начале перетаскивания (если нужно)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Когда пользователь отпустил ползунок, обновляем уровни каналов и отправляем данные
                updateLEDChannels()
                sendPWMValuesWithDelay()
            }
        })
    }



    // Настройка ползунка яркости
    private fun setupBrightnessSeekBar() {
        brightnessSeekBar.max = 100 / 5  // Ползунок яркости с шагом 5%
        brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                brightnessValue = progress * 5
                brightnessTextView.text = "Яркость: $brightnessValue%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Можно сделать что-то при начале перетаскивания (если нужно)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Когда пользователь отпустил ползунок, обновляем уровни каналов и отправляем данные
                updateLEDChannels()
                sendPWMValuesWithDelay()
            }
        })
    }


    // Обновление значений каналов на основе выбранной температуры и яркости
    private fun updateLEDChannels() {
        val normalizedBrightness = brightnessValue / 100.0

        // Логика распределения мощности на каналы
        if (temperatureValue <= 2800) {
            pwm2800K = (normalizedBrightness * 255).toInt()
            pwm4000K = 0
            pwm5000K = 0
            pwm5700K = 0
        } else if (temperatureValue >= 5700) {
            pwm2800K = 0
            pwm4000K = 0
            pwm5000K = 0
            pwm5700K = (normalizedBrightness * 255).toInt()
        } else {
            // Используем все 4 канала для промежуточных температур
            val tempRange = 5700 - 2800
            val relativeTemp = (temperatureValue - 2800).toFloat() / tempRange

            pwm2800K = ((1 - relativeTemp) * normalizedBrightness * 255).toInt()
            pwm4000K = ((1 - Math.abs(0.5f - relativeTemp) * 2) * normalizedBrightness * 255).toInt()
            pwm5000K = ((1 - Math.abs(0.5f - relativeTemp) * 2) * normalizedBrightness * 255).toInt()
            pwm5700K = (relativeTemp * normalizedBrightness * 255).toInt()
        }

        // Обновляем слайдеры
        seekBar2800K.progress = (pwm2800K * 100) / 255
        seekBar4000K.progress = (pwm4000K * 100) / 255
        seekBar5000K.progress = (pwm5000K * 100) / 255
        seekBar5700K.progress = (pwm5700K * 100) / 255

        // Отправляем обновленные значения PWM на устройство
        sendPWMValues()
    }



    private fun sendPWMValues() {
        SendPWMTask().execute(pwm2800K, pwm4000K, pwm5000K, pwm5700K)
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
    // Функция для отправки PWM с задержкой
    private fun sendPWMValuesWithDelay() {
        pwmUpdateRunnable?.let { pwmUpdateHandler.removeCallbacks(it) }  // Отменяем предыдущий запрос
        pwmUpdateRunnable = Runnable {
            sendPWMValues()  // Здесь вызывается функция отправки PWM
        }
        pwmUpdateHandler.postDelayed(pwmUpdateRunnable!!, pwmUpdateDelay)  // Устанавливаем задержку
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
        private fun sendPWMValuesWithDelay() {
            pwmUpdateRunnable?.let { pwmUpdateHandler.removeCallbacks(it) }  // Отменяем предыдущий запрос
            pwmUpdateRunnable = Runnable {
                sendPWMValues()  // Здесь вызывается функция отправки PWM
            }
            pwmUpdateHandler.postDelayed(pwmUpdateRunnable!!, pwmUpdateDelay)  // Устанавливаем задержку
        }
    }
}

