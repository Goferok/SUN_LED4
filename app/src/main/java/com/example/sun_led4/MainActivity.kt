package com.example.sun_led4

import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.Executors

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

    // Переменная для хранения IP-адреса выбранного устройства
    private var selectedDeviceIP: String? = null

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

        // Настройка слушателя выбора устройства из Spinner
        espDeviceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDeviceIP = espList[position] // Сохраняем выбранный IP-адрес
                Toast.makeText(this@MainActivity, "Выбрано устройство: $selectedDeviceIP", Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedDeviceIP = null
            }
        }

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
            scanLocalNetwork()
        }
    }

    // Функция для настройки ползунков
    private fun setupSeekBar(seekBar: SeekBar, percentView: TextView, onChange: (Int) -> Unit) {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                percentView.text = "$progress%"
                onChange(progress)
                updateTemperature()

                val scaledPwm2800K = (pwm2800K * 255) / 50
                val scaledPwm4000K = (pwm4000K * 255) / 50
                val scaledPwm5000K = (pwm5000K * 255) / 50
                val scaledPwm5700K = (pwm5700K * 255) / 50

                // Отправляем значения только если устройство выбрано
                selectedDeviceIP?.let { ip ->
                    SendPWMTask(ip).execute(scaledPwm2800K, scaledPwm4000K, scaledPwm5000K, scaledPwm5700K)
                } ?: Toast.makeText(this@MainActivity, "Выберите устройство из списка", Toast.LENGTH_SHORT).show()
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

    // Функция для поиска ESP32 через запросы по IP-адресам
    private fun scanLocalNetwork() {
        val executor = Executors.newFixedThreadPool(10)

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

                        // Чтение ответа от устройства
                        val responseCode = connection.responseCode
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            val inputStream = connection.inputStream.bufferedReader().use { it.readText() }

                            // Проверяем, что ответ содержит "SUNLED"
                            if (inputStream.contains("SUNLED")) {
                                runOnUiThread {
                                    espList.add(ip)
                                    espListAdapter.notifyDataSetChanged()
                                    deviceListLabel.visibility = View.VISIBLE
                                    espDeviceSpinner.visibility = View.VISIBLE
                                    Toast.makeText(this@MainActivity, "Найдено устройство: $ip", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Log.d("SCAN", "Устройство на IP $ip не ответило 'SUNLED'. Ответ: $inputStream")
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Задача для отправки SSID и пароля на ESP32
    private inner class SendWiFiDataTask : AsyncTask<String, Void, Void>() {
        override fun doInBackground(vararg params: String?): Void? {
            val ssid = params[0] ?: return null
            val password = params[1] ?: return null
            val url = URL("http://192.168.4.1:80/setWiFi")  // Используем выбранное устройство
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
                    println("WiFi данные отправлены успешно")
                } else {
                    println("Ошибка при отправке WiFi данных: $responseCode")
                }
            }
            return null
        }
    }

    // Задача для отправки данных PWM
    private inner class SendPWMTask(private val ip: String) : AsyncTask<Int, Void, Void>() {
        override fun doInBackground(vararg pwmValues: Int?): Void? {
            val pwm2800K = pwmValues[0] ?: return null
            val pwm4000K = pwmValues[1] ?: return null
            val pwm5000K = pwmValues[2] ?: return null
            val pwm5700K = pwmValues[3] ?: return null
            sendPWMValues(pwm2800K, pwm4000K, pwm5000K, pwm5700K, ip)
            return null
        }

        private fun sendPWMValues(pwm2800K: Int, pwm4000K: Int, pwm5000K: Int, pwm5700K: Int, ip: String) {
            val url = URL("http://$ip:80/setPWM")  // Отправляем на выбранное устройство
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
                    println("PWM данные отправлены успешно")
                } else {
                    println("Ошибка при отправке PWM данных: $responseCode")
                }
            }
        }
    }
}

