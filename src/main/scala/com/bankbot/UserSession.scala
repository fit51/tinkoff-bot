package com.bankbot

import akka.actor.{Actor, ActorLogging, Props}

import telegram.TelegramTypes._

/**
  * Actor that authenticates and handles commands for single user
  * Ex. /balance, /history
  */

object UserSession {
  def props(chat: Chat) = Props(new UserSession(chat: Chat))

}

class UserSession(chat: Chat) extends Actor with ActorLogging {
  override def receive: Receive = ???
}
