package com.bankbot

import akka.actor.{Actor, ActorLogging, Props}
import telegram.TelegramTypes._

/**
  * Master actor
  * Creates and manages UserSessions
  */

object SessionManager {
  def props = Props(new SessionManager())

}

class SessionManager extends Actor with ActorLogging {

  def createUserSession(chat: Chat) =
    context.actorOf(UserSession.props(chat), chat.id.toString)

  override def receive: Receive = {
    case Message(message_id: Int, from: User, chat: Chat, date: Int, text: Option[String]) => {

    }
  }
}
