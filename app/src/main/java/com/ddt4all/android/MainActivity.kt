
package com.ddt4all.android

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 32, 24, 24)
            setBackgroundColor(Color.rgb(245, 247, 250))
        }

        val title = TextView(this).apply {
            text = "DDT4ALL Android"
            textSize = 28f
            setTextColor(Color.rgb(25, 55, 90))
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Диагностика Renault"
            textSize = 18f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }

        val status = TextView(this).apply {
            text = "Адаптер ELM327: не подключён"
            textSize = 16f
            setTextColor(Color.rgb(180, 70, 50))
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 24)
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(status)

        val buttons = listOf(
            "Подключение ELM327",
            "Идентификация ЭБУ",
            "Чтение ошибок",
            "Удаление ошибок",
            "Параметры в реальном времени",
            "Конфигурация блоков",
            "Исполнительные тесты"
        )

        buttons.forEach { name ->
            val button = Button(this).apply {
                text = name
                textSize = 15f
                setOnClickListener {
                    Toast.makeText(
                        this@MainActivity,
                        "Функция будет подключена на следующем этапе",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            layout.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        setContentView(layout)
    }
}
