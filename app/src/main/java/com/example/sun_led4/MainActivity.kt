package com.example.sun_led4

import android.content.Context
import android.net.wifi.WifiManager
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.DatagramPacket
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import android.view.View
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private var pwmUpdateHandler = Handler(Looper.getMainLooper())
    private var pwmUpdateRunnable: Runnable? = null
    private val pwmUpdateDelay: Long = 100  // Задержка 100 мс между отправками
    private lateinit var switchRelayButton: Button
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
    private var isLampOn = false // Переменная для отслеживания состояния лампы
    private var lastPwmValues = intArrayOf(0, 0, 0, 0) // Массив для хранения последних значений PWM

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
        switchRelayButton = findViewById(R.id.switchRelayButton)
        espListAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, espList)
        espListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        espDeviceSpinner.adapter = espListAdapter


        // Добавляем обработчик выбора элемента в спиннере
        espDeviceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                fetchCurrentState()  // Запрос текущего состояния выбранного устройства
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Ничего не делаем, если ничего не выбрано
            }
        }

        // Загрузка сохраненных устройств
        loadSavedDevices()

        findESPButton.setOnClickListener {
            findDevicesUsingMDNS()
        }
        switchRelayButton.setOnClickListener {
            toggleLamp() // Вызов функции переключения состояния лампы
        }

        // Настройка ползунков
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

        }

    }

    // Определение метода sendPWMValues, который вызывает SendPWMTask
    private fun sendPWMValues() {
        SendPWMTask().execute(pwm2800K, pwm4000K, pwm5000K, pwm5700K)
    }
    // Функция для обновления состояния лампы
    private fun updateLampState() {
        isLampOn = (pwm2800K > 0 || pwm4000K > 0 || pwm5000K > 0 || pwm5700K > 0)

        // Обновляем текст кнопки в зависимости от состояния лампы
        switchRelayButton.text = if (isLampOn) "Выключить лампу" else "Включить лампу"
    }
    // Функция для настройки ползунков
    private fun setupSeekBar(seekBar: SeekBar, percentView: TextView, onChange: (Int) -> Unit) {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Отображаем текущее значение в текстовом поле
                percentView.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Когда пользователь отпустил ползунок, обновляем значение PWM
                val progress = seekBar?.progress ?: 0
                onChange(progress)

                // Обновляем состояние лампы
                updateLampState()
                updateTemperature()  // Пересчёт температуры
                sendPWMValuesWithDelay()  // Отправляем PWM с задержкой
            }
        })
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
    // Поиск устройств ESP32
    private fun findDevicesUsingMDNS() {
        espList.clear()  // Очищаем текущий список устройств
        espListAdapter.notifyDataSetChanged()  // Обновляем адаптер спиннера

        AsyncTask.execute {
            try {
                // Инициализация JmDNS
                val jmdns = JmDNS.create(InetAddress.getByName("0.0.0.0")) // Используем любой адрес

                // Создаем слушателя сервисов
                val serviceListener = object : ServiceListener {
                    override fun serviceAdded(event: ServiceEvent) {
                        jmdns.requestServiceInfo(event.type, event.name) // Запрос информации о сервисе
                    }

                    override fun serviceRemoved(event: ServiceEvent) {
                        Log.d("FIND_DEVICES", "Сервис удален: ${event.name}")
                    }

                    override fun serviceResolved(event: ServiceEvent) {
                        val serviceInfo = event.info
                        val hostAddress = serviceInfo.hostAddresses.firstOrNull() // Получаем IP-адрес устройства

                        // Проверяем, содержится ли "esp" в имени сервиса
                        if (event.name.contains("esp", ignoreCase = true) && hostAddress != null) {
                            // Добавляем устройство в список
                            val deviceInfo = "$hostAddress (Online)"
                            espList.add(deviceInfo)

                            // Обновляем спиннер на основном потоке
                            runOnUiThread {
                                espListAdapter.notifyDataSetChanged()
                                Toast.makeText(this@MainActivity, "Устройство найдено: $deviceInfo", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // Регистрируем слушатель
                jmdns.addServiceListener("_http._tcp.local.", serviceListener)

                // Удерживаем программу открытой, пока не закончится поиск
                Thread.sleep(10000) // Установите желаемое время ожидания поиска

                // Закрытие JmDNS
                jmdns.close() // Закрываем JmDNS после завершения поиска
            } catch (e: Exception) {
                Log.e("FIND_DEVICES", "Ошибка при поиске устройств: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Ошибка при поиске устройств: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    //Получение состояния лампы с контроллера
    private fun fetchCurrentState() {
        val selectedDevice = espDeviceSpinner.selectedItem.toString()
        val ipAddress = selectedDevice.substringBefore(" (") // Извлечение IP адреса

        val url = URL("http://$ipAddress/getState")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("FETCH_STATE", "Запрос к $url")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                val responseCode = connection.responseCode
                Log.d("FETCH_STATE", "Код ответа: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val jsonResponse = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        jsonResponse.append(line)
                    }
                    reader.close()

                    // Парсинг JSON
                    val jsonObject = JSONObject(jsonResponse.toString())
                    pwm2800K = jsonObject.getInt("pwm2800K")
                    pwm4000K = jsonObject.getInt("pwm4000K")
                    pwm5000K = jsonObject.getInt("pwm5000K")
                    pwm5700K = jsonObject.getInt("pwm5700K")
                    brightnessValue = jsonObject.getInt("brightness")
                    val relayState = jsonObject.getBoolean("relayState")

                    // Обновление ползунков и состояний в UI
                    withContext(Dispatchers.Main) {
                        updateSeekBars()
                        switchRelayButton.text = if (relayState) "Выключить лампу" else "Включить лампу"
                    }
                } else {
                    Log.e("FETCH_STATE", "Ошибка получения состояния: $responseCode")
                }
            } catch (e: Exception) {
                Log.e("FETCH_STATE", "Ошибка: ${e.message}")
            }
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
    // Функция для обновления состояния реле
    private fun updateRelayState() {
        // Если хотя бы один ползунок не равен 0, то реле включается
        if (isLampOn) {
            sendRelayState(true)  // Отправляем команду на включение реле
        } else {
            sendRelayState(false)  // Отправляем команду на выключение реле
        }
    }

    // Функция для отправки состояния реле на устройство
    private fun sendRelayState(state: Boolean) {
        val selectedDevice = espDeviceSpinner.selectedItem.toString()
        val ipAddress = selectedDevice.substringBefore(" (") // Извлекаем IP-адрес устройства

        val url = URL("http://$ipAddress/setRelay?state=${if (state) "on" else "off"}")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d("SEND_RELAY_STATE", "Состояние реле успешно обновлено.")
                } else {
                    Log.e("SEND_RELAY_STATE", "Ошибка обновления состояния реле: $responseCode")
                }
            } catch (e: Exception) {
                Log.e("SEND_RELAY_STATE", "Ошибка: ${e.message}")
            }
        }
    }
    // Функция для переключения состояния лампы
    private fun toggleLamp() {
        if (isLampOn) {
            // Сохраняем текущие значения ползунков
            lastPwmValues[0] = pwm2800K
            lastPwmValues[1] = pwm4000K
            lastPwmValues[2] = pwm5000K
            lastPwmValues[3] = pwm5700K

            // Выключаем лампу, выставляем все ползунки в 0
            pwm2800K = 0
            pwm4000K = 0
            pwm5000K = 0
            pwm5700K = 0

            // Обновляем ползунки
            updateSeekBars()

            // Обновляем состояние лампы
            isLampOn = false
            switchRelayButton.text = "Включить лампу" // Обновляем текст кнопки
        } else {
            // Включаем лампу, восстанавливаем последние сохраненные значения
            pwm2800K = lastPwmValues[0]
            pwm4000K = lastPwmValues[1]
            pwm5000K = lastPwmValues[2]
            pwm5700K = lastPwmValues[3]

            // Обновляем ползунки
            updateSeekBars()

            // Обновляем состояние лампы
            isLampOn = true
            switchRelayButton.text = "Выключить лампу" // Обновляем текст кнопки
        }

        // Обновляем состояние реле
        updateRelayState()

        // Отправляем новые PWM значения на устройство
        sendPWMValues()
    }
    private fun scalePWM(pwmValue: Int): Int {
        return (pwmValue * 2.55).toInt().coerceIn(0, 255)
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
    private fun updateSeekBars() {
        // Обновление UI для ползунков
        seekBar2800K.progress = (scalePWM(pwm2800K) * 100) / 255
        seekBar4000K.progress = (scalePWM(pwm4000K) * 100) / 255
        seekBar5000K.progress = (scalePWM(pwm5000K) * 100) / 255
        seekBar5700K.progress = (scalePWM(pwm5700K) * 100) / 255
    }

    // Проверяем доступность устройства
    private fun isDeviceAvailable(ipAddress: String): Boolean {
        return try {
            // Проверяем доступность устройства с помощью сокета
            val socket = Socket()
            socket.connect(InetSocketAddress(ipAddress, 80), 1000) // Тайм-аут 1 секунда
            socket.close()
            true
        } catch (e: Exception) {
            Log.e("IS_DEVICE_AVAILABLE", "Ошибка проверки доступности устройства: ${e.message}")
            false
        }
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

    // Загрузка сохраненных устройств
    private fun loadSavedDevices() {
        val sharedPreferences = getSharedPreferences("SavedDevices", MODE_PRIVATE)
        val savedDevices = sharedPreferences.getStringSet("devices", null)
        savedDevices?.forEach { device -> espList.add(device) }
        espListAdapter.notifyDataSetChanged()
    }

    // Метод для отправки PWM значений с задержкой
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

            // Получаем выбранное устройство из спиннера
            val selectedDevice = espDeviceSpinner.selectedItem.toString()
            val ipAddress = selectedDevice.substringBefore(" (") // Извлекаем IP-адрес из строки

            // Проверка подключения к ESP32 перед отправкой
            if (isDeviceAvailable(ipAddress)) {
                sendPWMValues(ipAddress, pwm2800K, pwm4000K, pwm5000K, pwm5700K)
            } else {
                Log.e("SEND_PWM_TASK", "Устройство недоступно. Не удалось отправить PWM значения.")
            }
            return null
        }


        // Метод для отправки PWM значений
        private fun sendPWMValues() {
            SendPWMTask().execute(pwm2800K, pwm4000K, pwm5000K, pwm5700K)
        }
        private fun sendPWMValues(ipAddress: String, pwm2800K: Int, pwm4000K: Int, pwm5000K: Int, pwm5700K: Int) {
            val scaledPwm2800K = scalePWM(pwm2800K)
            val scaledPwm4000K = scalePWM(pwm4000K)
            val scaledPwm5000K = scalePWM(pwm5000K)
            val scaledPwm5700K = scalePWM(pwm5700K)

            val url = URL("http://$ipAddress:80/setPWM") // Используем IP-адрес устройства
            val postData = "2800K=$scaledPwm2800K&4000K=$scaledPwm4000K&5000K=$scaledPwm5000K&5700K=$scaledPwm5700K"

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
                    Log.d("SEND_PWM_TASK", "PWM values sent successfully to $ipAddress.")
                } else {
                    Log.e("SEND_PWM_TASK", "Error sending PWM values to $ipAddress: $responseCode")
                }
            }
        }

    }


}

