package com.example.sun_led4

import android.content.Context
import android.net.wifi.WifiManager
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import org.json.JSONObject


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

        // Если устройство уже выбрано, запросим состояние контроллера
        if (espDeviceSpinner.selectedItem != null) {
            requestControllerState()
        }

        // Загрузка сохраненных устройств
        loadSavedDevices()

        // Если есть устройства в списке, выбираем первое и запрашиваем состояние
        if (espList.isNotEmpty()) {
            espDeviceSpinner.setSelection(0)
            requestControllerState() // Запрашиваем состояние контроллера
        }

        findESPButton.setOnClickListener {
            checkControllerConnection()
            // Запрашиваем текущее состояние контроллера после его обнаружения
            requestControllerState()
        }

        // Настройка ползунков для каждого канала
        setupSeekBar(seekBar2800K, percent2800K) { pwm2800K = it }
        setupSeekBar(seekBar4000K, percent4000K) { pwm4000K = it }
        setupSeekBar(seekBar5000K, percent5000K) { pwm5000K = it }
        setupSeekBar(seekBar5700K, percent5700K) { pwm5700K = it }

        // Внутри onCreate добавляем обработчик для кнопки включения/выключения реле
        val switchRelayButton: Button = findViewById(R.id.switchRelayButton)
        var isRelayOn: Boolean = false  // Переменная для отслеживания состояния реле

        switchRelayButton.setOnClickListener {
            // Переключаем состояние реле
            isRelayOn = !isRelayOn
            val relayState = if (isRelayOn) "on" else "off"

            // Обновляем текст кнопки в зависимости от состояния реле
            switchRelayButton.text = if (isRelayOn) "Выключить лампу" else "Включить лампу"

            // Отправляем команду на ESP32 для изменения состояния реле
            SendRelayTask().execute(relayState)
        }


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

    // Определение метода sendPWMValues, который вызывает SendPWMTask
    private fun sendPWMValues() {
        SendPWMTask().execute(pwm2800K, pwm4000K, pwm5000K, pwm5700K)
    }

    // Функция для настройки ползунков
    private fun setupSeekBar(seekBar: SeekBar, percentView: TextView, onChange: (Int) -> Unit) {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Отображаем текущее значение в текстовом поле, но не отправляем на контроллер
                percentView.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Когда пользователь отпустил ползунок, обновляем значение PWM
                val progress = seekBar?.progress ?: 0
                onChange(progress)
                updateTemperature()  // Пересчёт температуры
                sendPWMValuesWithDelay()  // Отправляем PWM с задержкой, чтобы избежать частых обновлений
            }
        })
    }

    private inner class SendRelayTask : AsyncTask<String, Void, Void>() {
        override fun doInBackground(vararg params: String?): Void? {
            val relayState = params[0] ?: return null

            // Получаем IP-адрес выбранного устройства
            val selectedDevice = espDeviceSpinner.selectedItem.toString()
            val ipAddress = selectedDevice.substringAfter("(").substringBefore(")")
            val url = URL("http://$ipAddress:80/setRelay")

            // Формируем POST-запрос с состоянием реле
            val postData = "state=$relayState"

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
                    println("Relay state sent successfully: $relayState")
                } else {
                    println("Error sending relay state: $responseCode")
                }
            }
            return null
        }
    }

    // Обновление значений температуры
    private fun updateTemperature() {
        val totalPWM = pwm2800K + pwm4000K + pwm5000K + pwm5700K
        if (totalPWM > 0) {
            val temperature =
                (pwm2800K * 2800 + pwm4000K * 4000 + pwm5000K * 5000 + pwm5700K * 5700) / totalPWM
            temperatureTextView.text = "Температура свечения: ${temperature}K"
        } else {
            temperatureTextView.text = "Температура свечения: N/A"
        }
    }

    // Настройка ползунка температуры
    private fun setupTemperatureSeekBar() {
        temperatureSeekBar.max = 2900  // Разница между 2800 и 5700K

        // Устанавливаем шаг в 100 единиц
        temperatureSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Округляем значение до ближайших 100
                val adjustedProgress = (progress / 100) * 100
                temperatureValue = 2800 + adjustedProgress
                temperatureTextView.text = "Температура: $temperatureValue K"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                updateLEDChannels()
            }
        })
    }

    // Настройка ползунка яркости
    private fun setupBrightnessSeekBar() {
        brightnessSeekBar.max = 100
        brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val adjustedProgress = (progress / 5) * 5
                brightnessValue = adjustedProgress
                brightnessTextView.text = "Яркость: $brightnessValue%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                updateLEDChannels()
            }
        })
    }

    // Обновление значений каналов на основе температуры и яркости
    private fun updateLEDChannels() {
        val normalizedBrightness = brightnessValue / 100.0

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
            val tempRange = 5700 - 2800
            val relativeTemp = (temperatureValue - 2800).toFloat() / tempRange

            pwm2800K = ((1 - relativeTemp) * normalizedBrightness * 255).toInt()
            pwm4000K =
                ((1 - Math.abs(0.5f - relativeTemp) * 2) * normalizedBrightness * 255).toInt()
            pwm5000K =
                ((1 - Math.abs(0.5f - relativeTemp) * 2) * normalizedBrightness * 255).toInt()
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

    // Загрузка сохраненных устройств
    private fun loadSavedDevices() {
        val sharedPreferences = getSharedPreferences("SavedDevices", MODE_PRIVATE)
        val savedDevices = sharedPreferences.getStringSet("devices", null)
        savedDevices?.forEach { device ->
            espList.add(device)
        }
        espListAdapter.notifyDataSetChanged()
    }
    private fun requestControllerState() {
        val selectedDevice = espDeviceSpinner.selectedItem.toString()
        val ipAddress = selectedDevice.substringAfter("(").substringBefore(")")
        val url = URL("http://$ipAddress:80/getState")

        AsyncTask.execute {
            try {
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000  // Увеличьте таймаут до 10 секунд

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d("RESPONSE", "Ответ от сервера: $inputStream")
                    val jsonObject = JSONObject(inputStream)

                    runOnUiThread {
                        // Обновление ползунков и переключателя реле на основе данных с контроллера
                        pwm2800K = jsonObject.getInt("pwm2800K")
                        pwm4000K = jsonObject.getInt("pwm4000K")
                        pwm5000K = jsonObject.getInt("pwm5000K")
                        pwm5700K = jsonObject.getInt("pwm5700K")
                        brightnessValue = jsonObject.getInt("brightness")
                        val relayState = jsonObject.getBoolean("relayState")

                        // Обновляем ползунки
                        seekBar2800K.progress = (pwm2800K * 100) / 255
                        seekBar4000K.progress = (pwm4000K * 100) / 255
                        seekBar5000K.progress = (pwm5000K * 100) / 255
                        seekBar5700K.progress = (pwm5700K * 100) / 255
                        brightnessSeekBar.progress = brightnessValue

                        // Обновляем состояние реле
                        val switchRelayButton: Button = findViewById(R.id.switchRelayButton)
                        switchRelayButton.text = if (relayState) "Выключить лампу" else "Включить лампу"
                    }
                } else {
                    Log.e("ERROR", "Ошибка получения состояния: $responseCode")
                }
            } catch (e: Exception) {
                Log.e("ERROR", "Ошибка соединения: ${e.message}")
                e.printStackTrace()
            }
        }
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

    // Поиск контроллера ESP32
    private fun checkControllerConnection() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val currentSSID = wifiManager.connectionInfo.ssid

        // Проверяем подключение к сети контроллера
        if (currentSSID.contains("ESP32_AP")) {
            espList.add("192.168.4.1")
            espListAdapter.notifyDataSetChanged()
            Toast.makeText(this@MainActivity, "Подключено к контроллеру", Toast.LENGTH_SHORT).show()
        } else {
            scanLocalNetwork()
        }
    }

    // Сканирование локальной сети для устройств ESP32
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
                            val inputStream =
                                connection.inputStream.bufferedReader().use { it.readText() }

                            if (inputStream.contains("SUNLED")) {
                                runOnUiThread {
                                    val deviceString = "$ip (Online)"
                                    if (!espList.contains(deviceString)) {
                                        espList.add(deviceString)
                                        espListAdapter.notifyDataSetChanged()
                                        newDevicesFound = true
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Найдено новое устройство: $ip",
                                            Toast.LENGTH_SHORT
                                        ).show()
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

        // Сообщение по завершении поиска
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)

        runOnUiThread {
            if (!newDevicesFound) {
                Toast.makeText(this@MainActivity, "Новых устройств не найдено", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    // Показ диалога ввода имени устройства
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
                Toast.makeText(this, "Имя устройства не может быть пустым", Toast.LENGTH_SHORT)
                    .show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    // Отправка данных Wi-Fi на ESP32
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

    // Отправка данных PWM с задержкой
    private fun sendPWMValuesWithDelay() {
        pwmUpdateRunnable?.let { pwmUpdateHandler.removeCallbacks(it) }  // Отменяем предыдущий запрос
        pwmUpdateRunnable = Runnable {
            sendPWMValues()
        }
        pwmUpdateHandler.postDelayed(pwmUpdateRunnable!!, pwmUpdateDelay)  // Устанавливаем задержку
    }

    // Отправка данных PWM на устройство
    private inner class SendPWMTask : AsyncTask<Int, Void, Void>() {
        override fun doInBackground(vararg pwmValues: Int?): Void? {
            val pwm2800K = pwmValues[0] ?: return null
            val pwm4000K = pwmValues[1] ?: return null
            val pwm5000K = pwmValues[2] ?: return null
            val pwm5700K = pwmValues[3] ?: return null
            sendPWMValues(pwm2800K, pwm4000K, pwm5000K, pwm5700K)
            return null
        }

        private fun scalePWM(pwmValue: Int): Int {
            return (pwmValue * 2.55).toInt().coerceIn(0, 255)
        }

        private fun sendPWMValues(pwm2800K: Int, pwm4000K: Int, pwm5000K: Int, pwm5700K: Int) {
            val scaledPwm2800K = scalePWM(pwm2800K)
            val scaledPwm4000K = scalePWM(pwm4000K)
            val scaledPwm5000K = scalePWM(pwm5000K)
            val scaledPwm5700K = scalePWM(pwm5700K)

            val selectedDevice = espDeviceSpinner.selectedItem.toString()
            val ipAddress = selectedDevice.substringAfter("(").substringBefore(")")
            val url = URL("http://$ipAddress:80/setPWM")
            val postData =
                "2800K=$scaledPwm2800K&4000K=$scaledPwm4000K&5000K=$scaledPwm5000K&5700K=$scaledPwm5700K"

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


