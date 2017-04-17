package com.bankbot

import akka.actor.{ActorContext, ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.bankbot.UserSession.SessionCommand
import com.bankbot.telegram.TelegramApi
import com.bankbot.telegram.TelegramTypes._
import com.bankbot.tinkoff.TinkoffApi
import org.mockito.Matchers.any
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.{verify, times => ts}
import org.mockito.Matchers.{eq => exact}
import telegram.PrettyMessage.prettyThx4Contact

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
  val tinkoffApiTest = mock[TinkoffApi]
  val testUser1 = User(321, "", Some(""), Some(""))
  val testUser2 = User(2, "", Some(""), Some(""))
  val testChat = Chat(123, "private")
  val testContact1 = Contact("999999", "Oleg", None, 1)
  val testContact2 = Contact("888888", "Tinkoff", None, 2)
  val testMessage1 = Message(0, Some(testUser1), testChat, 0, None, None, None)
  val testMessage2 = Message(1, Some(testUser2), testChat, 0, None, None, Some(testContact2))

  val testUserSession = TestProbe(testChat.id.toString)
  val sessionManager = system.actorOf(Props(
    new SessionManager(telegramApiTest, tinkoffApiTest) {
      override def createUserSession(name: String, command: SessionCommand): ActorRef = testUserSession.ref
    }
  ))

  "SessionManager" must {
    "send a message about sharing the contact when sent SendBalance and" +
      " the user is not in the contact list" in {
      sessionManager ! UserSession.BalanceCommand(testUser1, testChat.id)
      val keyboardButton = KeyboardButton("Send My Phone Number", Some(true))
      val replyKeyboardMarkup = ReplyKeyboardMarkup(Vector(Vector(keyboardButton)), Some(true), Some(true))
      val message = TelegramMessage(testMessage1.chat.id, "This operation requires your phone number.",
        reply_markup = Some(Right(replyKeyboardMarkup)))
      eventually {
        verify(telegramApiTest).sendMessage(exact(message))(any(classOf[ActorContext]),
          any(classOf[LoggingAdapter]))
      }
    }

    "send message \"Thanks for sharing your contact!\" if gets a message with contact" in {
      sessionManager ! SessionManager.PossibleContact(testMessage2.chat.id, testContact2)
      val message = TelegramMessage(testMessage2.chat.id, prettyThx4Contact)
      eventually {
        verify(telegramApiTest).sendMessage(exact(message))(any(classOf[ActorContext]),
          any(classOf[LoggingAdapter]))
      }
    }

    "create UserSession and forward message if the user is in the contact list" in {
      val command = UserSession.BalanceCommand(testUser2, testChat.id)
      sessionManager ! command
      testUserSession.expectMsg(command)
    }
  }

}
