package com.valoracloud.api

import java.security.SecureRandom

fun cuid(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    val random = SecureRandom()
    return (1..25).map { chars[random.nextInt(chars.length)] }.joinToString("")
}
