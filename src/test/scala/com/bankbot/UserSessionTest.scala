package com.bankbot

import akka.actor.{ActorContext, ActorRef, ActorSystem, Props, Terminated}
import akka.event.LoggingAdapter
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.bankbot.SessionManager.WaitForReply
import com.bankbot.UserSession.{BalanceCommand, GetAccessLevel, Reply}
import com.bankbot.telegram.TelegramApi
import com.bankbot.telegram.TelegramTypes._
import com.bankbot.tinkoff.TinkoffApi
import com.bankbot.tinkoff.TinkoffTypes._
import com.miguno.akka.testing.VirtualTime
import org.mockito.Mockito.{verify, when => w}
import org.mockito.Matchers.{eq => exact, _}
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by pavel on 11.04.2017.
  */
class UserSessionTest extends TestKit(ActorSystem("testBotSystem"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with MockitoSugar
  with Eventually
  with StopSystemAfterAll {
  val testUser = User(321, "", Some(""), Some(""))
  val testChat = Chat(123, "private")
  val testReplyMessage = ReplyMessage(320, Some(testUser), testChat, 0, Some("Please enter sms code"), None)
  val testReply = Reply(320, "1234")
  val testContact = Contact("79992223344", "Tom", None, 1)
  val testSession = Session("OK", "sessionid")
  val testSignUp = SignUp("OK", "someticket", Vector("SMSBYID"))
  val testConfirm = Confirm("OK", Some(ConfirmPayLoad("", "sessionid", 1, AdditionalAuth(false, false, false), "")))

  def createFixture = {
    val tinkoffApiTest = mock[TinkoffApi]
    val telegramApiTest = mock[TelegramApi]
    val time = new VirtualTime
    val parent = TestProbe()
    (
      tinkoffApiTest,
      telegramApiTest,
      time,
      parent
    )
  }

  "NoSessionActions" must {

    "call getSession on start" in {
      val (tinkoffApiTest, telegramApiTest, time, parent) = createFixture
      val userSession = parent.childActorOf(
        UserSession.props(1, testContact, BalanceCommand(testUser, 1), telegramApiTest, tinkoffApiTest, time.scheduler)
      )
      eventually {
        verify(tinkoffApiTest).getSession()(
          any(classOf[ActorContext]), any(classOf[LoggingAdapter]), any(classOf[ActorRef]
          ))
      }
    }

    "call signUp with session_id and user phone, when Session is received" in {
      val (tinkoffApiTest, telegramApiTest, time, parent) = createFixture
      val userSession = parent.childActorOf(
        UserSession.props(1, testContact, BalanceCommand(testUser, 1), telegramApiTest, tinkoffApiTest, time.scheduler)
      )
      userSession ! testSession
      eventually {
        verify(tinkoffApiTest).signUp(
          exact(testSession.payload),
          exact("+" + testContact.phone_number))(
          any(classOf[ActorContext]), any(classOf[LoggingAdapter]), any(classOf[ActorRef]
          ))
      }
    }

    "request SMSID, and ask parent Wait for Reply, whem SignUp is received" in {
      import system.dispatcher
      val (tinkoffApiTest, telegramApiTest, time, parent) = createFixture
      val userSession = parent.childActorOf(
        UserSession.props(1, testContact, BalanceCommand(testUser, 1), telegramApiTest, tinkoffApiTest, time.scheduler)
      )
      w(telegramApiTest.sendReplyMessage(
        any(classOf[Map[String, String]]))(
        any(classOf[ActorContext]), any(classOf[LoggingAdapter]), any(classOf[ActorRef]
        ))).thenReturn(
        Future(ServerAnswerReply(true, testReplyMessage))
      )
      userSession ! testSignUp
      eventually {
        verify(telegramApiTest).sendReplyMessage(
          any(classOf[Map[String, String]]))(
          any(classOf[ActorContext]), any(classOf[LoggingAdapter]), any(classOf[ActorRef]
          ))
      }
      parent.expectMsg(WaitForReply(testChat.id, testReplyMessage.message_id))
    }

    "call confirm when receives user SMSID reply" in {
      val (tinkoffApiTest, telegramApiTest, time, parent) = createFixture
      val userSession = parent.childActorOf( Props(
        new UserSession(1, testContact, BalanceCommand(testUser, 1), telegramApiTest, tinkoffApiTest, time.scheduler) {
          override def requestSMSId(text: String) = Unit
        })
      )
      userSession ! testSession
      userSession ! testSignUp
      userSession ! testReply
      eventually {
        verify(tinkoffApiTest).confirm(
          exact(testSession.payload),
          exact(testSignUp.operationTicket),
          exact(testReply.text))(
          any(classOf[ActorContext]), any(classOf[LoggingAdapter]), any(classOf[ActorRef]
          ))
      }
    }

    "call LevelUp when receives Confirm" in {
      val (tinkoffApiTest, telegramApiTest, time, parent) = createFixture
      val userSession = parent.childActorOf(
        UserSession.props(1, testContact, BalanceCommand(testUser, 1), telegramApiTest, tinkoffApiTest, time.scheduler)
      )
      userSession ! testConfirm
      eventually {
        verify(tinkoffApiTest).levelUp(
          exact(testSession.payload))(
          any(classOf[ActorContext]), any(classOf[LoggingAdapter]), any(classOf[ActorRef]
          ))
      }
    }

    "become Registered when receive success LevelUp and send initial command" in {
      val (tinkoffApiTest, telegramApiTest, time, parent) = createFixture
      val userSession = parent.childActorOf( Props(
        new UserSession(1, testContact, BalanceCommand(testUser, 1), telegramApiTest, tinkoffApiTest, time.scheduler) {
          override def processAccounts(f: (AccountsFlat) => String): Unit = {
            tinkoffApiTest.accountsFlat(session)
          }
        })
      )
      userSession ! testSession
      userSession ! testConfirm
      userSession ! LevelUp("OK", AccessLevel("REGISTERED"))
      userSession ! GetAccessLevel
      expectMsg("REGISTERED")
      eventually {
        verify(tinkoffApiTest).accountsFlat(
          exact(testSession.payload))(
          any(classOf[ActorContext]), any(classOf[LoggingAdapter]), any(classOf[ActorRef]
          ))
      }
    }

    "when Registered call WarmUp Session" in {
      val (tinkoffApiTest, telegramApiTest, time, parent) = createFixture
      val userSession = parent.childActorOf( Props(
        new UserSession(1, testContact, BalanceCommand(testUser, 1), telegramApiTest, tinkoffApiTest, time.scheduler) {
          override def processAccounts(f: (AccountsFlat) => String): Unit = {
            tinkoffApiTest.accountsFlat(session)
          }
        })
      )
      userSession ! testSession
      userSession ! testConfirm
      userSession ! LevelUp("OK", AccessLevel("REGISTERED"))
      eventually {
        verify(tinkoffApiTest).warmupCache(
          exact(testSession.payload))(
          any(classOf[ActorContext]), any(classOf[LoggingAdapter]))
      }
    }

    "kill self after 5 minutes waiting for User Message" in {
      val (tinkoffApiTest, telegramApiTest, time, parent) = createFixture
      val userSession = parent.childActorOf(
        UserSession.props(1, testContact, BalanceCommand(testUser, 1), telegramApiTest, tinkoffApiTest, time.scheduler)
      )
      parent watch  userSession
      userSession ! testSession
      time.advance(5 minutes)
      time.elapsed shouldBe (5 minutes)
      parent.expectTerminated(userSession)
    }

  }

}
