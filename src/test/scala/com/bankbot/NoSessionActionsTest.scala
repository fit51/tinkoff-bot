package com.bankbot

import java.time.{Instant, ZoneId}

import akka.actor.{ActorContext, ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.testkit.{ImplicitSender, TestKit}
import com.bankbot.NoSessionActions._
import com.bankbot.telegram.{PrettyMessage, TelegramApi}
import com.bankbot.telegram.TelegramTypes.{Chat, Message, TelegramMessage, User}
import com.bankbot.tinkoff.TinkoffApi
import com.bankbot.tinkoff.TinkoffTypes.{Currency, Rate}
import com.miguno.akka.testing.VirtualTime
import org.scalatest.mockito.MockitoSugar
import org.scalatest.concurrent.Eventually
import org.mockito.Mockito.{verify, times => ts}
import org.mockito.Matchers.{eq => exact, _}
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by pavel on 31.03.17.
  */
class NoSessionActionsTest extends TestKit(ActorSystem("testBotSystem"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with MockitoSugar
  with Eventually
  with StopSystemAfterAll {

  val tinkoffApiTest = mock[TinkoffApi]
  val telegramApiTest = mock[TelegramApi]
  val testUser = User(321, "", Some(""), Some(""))
  val testChat = Chat(123, "private")
  val testMessage = Message(0, Some(testUser), testChat, 0, None, None, None)
  val rateNew = Rate("", Currency(0, "USD"), Currency(0, "RUB"), 58.2f, 56.7f)

  val time = new VirtualTime
  val noSessionActions = system.actorOf(NoSessionActions.props(time.scheduler, 2000, telegramApiTest, tinkoffApiTest))
  time.advance(1 second)

  "NoSessionActions" must {
    "update Rates every 2 seconds" in {
      time.advance(5 second)
      time.elapsed shouldBe (6 second)
      eventually {
        verify(tinkoffApiTest, ts(3)).getRates()(any(classOf[ActorContext]),
          any(classOf[LoggingAdapter]),
          any(classOf[ActorRef]
          ))
      }
    }
    "set newest Rates when it receives a ServerAnswer" in {
      import com.bankbot.tinkoff.TinkoffTypes.ServerAnswer
      val rateOld = Rate("", Currency(0, "USD"), Currency(0, "RUB"), 57.55f, 55.3f)
      noSessionActions ! ServerAnswer(2L, Vector(rateNew))
      noSessionActions ! GetRates
      expectMsg(Vector(rateNew))
      noSessionActions ! ServerAnswer(1L, Vector(rateOld))
      noSessionActions ! GetRates
      expectMsg(Vector(rateNew))
    }
    "send Rates to telegram chat when requested" in {
      noSessionActions ! SendRates(testChat.id)
      val message = TelegramMessage(testChat.id, PrettyMessage.prettyRates(
        Instant.ofEpochMilli(2L), Vector(rateNew), ZoneId.of("Europe/Moscow")))
      eventually {
        verify(telegramApiTest).sendMessage(exact(message))(any(classOf[ActorContext]),
          any(classOf[LoggingAdapter]))
      }
    }
    "send reply to Telegram Message when requested" in {
      val testText = "test text"
      noSessionActions ! SendMessage(testChat.id, testText)
      val message = TelegramMessage(testChat.id, testText)
      eventually {
        verify(telegramApiTest).sendMessage(exact(message))(any(classOf[ActorContext]),
          any(classOf[LoggingAdapter]))
      }
    }
  }

}
