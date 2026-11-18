package com.example.smartspend.util

import java.util.Calendar

/** Thay cho chuỗi cứng "2026-10": luôn lấy tháng hiện tại của thiết bị. */
object CurrentMonth {
    private fun cal() = Calendar.getInstance()
    fun key(): String = "%04d-%02d".format(cal().get(Calendar.YEAR), cal().get(Calendar.MONTH) + 1) // 2026-11
    fun number(): Int = cal().get(Calendar.MONTH) + 1                                                 // 11
    fun label(): String = "${number()}/${cal().get(Calendar.YEAR)}"                                   // 11/2026
}
