package com.bankbot.telegram

import com.typesafe.config.ConfigFactory

/**
  * Created by pavel on 25.03.17.
  */
trait TelegramKey {

  val token = ConfigFactory.load("telegram").getString("telegram.key")


}
