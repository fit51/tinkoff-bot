package com.bankbot

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.bankbot.telegram.TelegramApi
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar

/**
  * Created by Nprone on 07.04.2017.
  */
class SessionManagerTest extends TestKit(ActorSystem("testBotSystem"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with MockitoSugar
  with Eventually
  with StopSystemAfterAll {

  val telegramApiTest = mock[TelegramApi]

}
