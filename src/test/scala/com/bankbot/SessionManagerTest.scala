package com.bankbot

import akka.actor.{ActorContext, ActorSystem}
import akka.event.LoggingAdapter
import akka.testkit.{ImplicitSender, TestKit}
import com.bankbot.telegram.TelegramApi
import com.bankbot.telegram.TelegramTypes.{Chat, Contact, Message, User}
import com.bankbot.tinkoff.TinkoffApi
import org.mockito.Matchers.any
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.{ verify, times => ts}
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

  val sessionManager = system.actorOf(SessionManager.props(telegramApiTest, tinkoffApiTest))
//  sessionManager.asInstanceOf[SessionManager].contacts += (2 -> testContact2)

  "SessionManager" must {
    "send a message about sharing the contact when sent SendBalance and" +
      " the user is not in the contact list" in {
      sessionManager ! UserSession.BalanceCommand(testUser1, testChat.id)
      val messageText = "This operation requires your phone number."
      val reply_markup = "{\"keyboard\":[[{\"text\":\"Send My Phone Number\", \"request_contact\": true}]], " +
        "\"resize_keyboard\": true, \"one_time_keyboard\": true}"
      val send = Map("chat_id" -> testMessage1.chat.id.toString, "text" -> messageText,
        "reply_markup" -> reply_markup)
      eventually {
        verify(telegramApiTest).sendMessage(exact(send))(any(classOf[ActorContext]),
          any(classOf[LoggingAdapter]))
      }
    }

    "send message \"Thanks for sharing your contact!\" if gets a message with contact" in {
      sessionManager ! SessionManager.PossibleContact(testMessage2.chat.id, testContact2)
      val send = Map("chat_id" -> testMessage2.chat.id.toString,
        "text" -> prettyThx4Contact, "parse_mode" -> "HTML")
      eventually {
        verify(telegramApiTest).sendMessage(exact(send))(any(classOf[ActorContext]),
          any(classOf[LoggingAdapter]))
      }
    }

    "send a message \"Your balance is: 0\" if the user is in the contact list" in {
//      sessionManager ! UserSession.BalanceCommand(testUser2, testChat.id)
//      val send = Map("chat_id" -> testMessage2.chat.id.toString,
//        "text" -> "Your balance is: 0", "parse_mode" -> "HTML")
//      eventually {
//        verify(telegramApiTest).sendMessage(exact(send))(any(classOf[ActorContext]),
//          any(classOf[LoggingAdapter]))
//      }
    }
  }

}
