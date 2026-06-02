package com.valoracloud.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication @EnableScheduling class ValoracloudApiApplication

fun main(args: Array<String>) {
    runApplication<ValoracloudApiApplication>(*args)
}
