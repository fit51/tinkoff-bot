package com.bankbot

import akka.actor.{ActorContext, ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.bankbot.telegram.TelegramApi
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.{ verify, times => ts}
import org.mockito.Matchers._
import org.scalatest.{Matchers, WordSpecLike}


/**
  * Created by pavel on 03.04.17.
  */
class TelegramUpdaterTest extends TestKit(ActorSystem("testBotSystem"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with MockitoSugar
  with Eventually
  with StopSystemAfterAll {
  import com.bankbot.telegram.TelegramTypes._
  import com.bankbot.NoSessionActions._
  import com.bankbot.TelegramUpdater._

  val telegramApiTest = mock[TelegramApi]
  val sessionManagerTest = TestProbe("sessionManager")
  val noSessionActionsTest = TestProbe("noSessionActions")
  val telegramUpdaterTest = system.actorOf(
      TelegramUpdater.props(sessionManagerTest.ref, noSessionActionsTest.ref, telegramApiTest)
    )
  val testChat = Chat(1, "private")
  val testUser = User(1, "Name", "LastName", "Login")
  val testMessage = Message(1, testUser, testChat, 123, Some("/bla"))
  val testUpdate = Update(2, testMessage)


  "Telegram Updater" must {
    "call getUpdates on Start and after every ServerAnswer" in {
      val failServerAnswer = ServerAnswer(false, Array(testUpdate))
      telegramUpdaterTest ! failServerAnswer
      eventually {
        verify(telegramApiTest, ts(2)).getUpdates(any(classOf[Int]))(any(classOf[ActorContext]),
          any(classOf[LoggingAdapter]), any(classOf[ActorRef]))
      }
    }
    "increase offset by update_id of every ServerAnswer" in {
      telegramUpdaterTest ! getOffset
      expectMsg(Offset(0))
      val withOffsetServerAnswer = ServerAnswer(true, Array(Update(1, testMessage)))
      telegramUpdaterTest ! withOffsetServerAnswer
      noSessionActionsTest.expectMsgClass(classOf[Reply])
      telegramUpdaterTest ! getOffset
      expectMsg(Offset(2))
    }
    "on unknown command reply to User See help via noSessionActions" in {
      val okServerAnswer = ServerAnswer(true, Array(testUpdate))
      telegramUpdaterTest ! okServerAnswer
      noSessionActionsTest.expectMsg(Reply(testMessage, "No Such Command\nSee /help"))
    }
    "on /rates forward message to NoSessionActions" in {
      val ratesMessage = Message(1, testUser, testChat, 123, Some("/rates"))
      val ratesServerAnswer = ServerAnswer(true, Array(Update(11, ratesMessage)))
      telegramUpdaterTest ! ratesServerAnswer
      noSessionActionsTest.expectMsg(SendRates(ratesMessage))
    }
  }
}