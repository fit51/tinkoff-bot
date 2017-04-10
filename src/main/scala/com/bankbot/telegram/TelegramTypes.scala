package com.bankbot.telegram

import spray.json._

/**
  * Case classes used in Telegram Api
  */

object TelegramTypes {

  case class KeyboardButton(text: String, request_contact: Boolean, request_location: Boolean)

  case class ReplyKeyboardMarkup(keyboard: Array[Array[KeyboardButton]], resize_keyboard: Boolean, one_time_keyboard: Boolean)

  case class Contact(phone_number: String, first_name: String, last_name: Option[String], user_id: Int)

  case class User(id: Int, first_name: String, last_name: Option[String], username: Option[String])

  case class Chat(id: Int, c_type: String)

  case class ReplyMessage(message_id: Int, from: Option[User], chat: Chat, date: Int,
                          text: Option[String], contact: Option[Contact])

  case class Message(message_id: Int, from: Option[User], chat: Chat, date: Int, reply_to_message: Option[ReplyMessage],
                     text: Option[String], contact: Option[Contact])

  case class Update(update_id: Int, message: Message)

  case class ServerAnswer(ok: Boolean, result: Array[Update])

}

/**
  * Json Marshalling for case classes used in Telegram Api
  */

trait MessageMarshallingTelegram extends DefaultJsonProtocol {

  import TelegramTypes._

  implicit val keyboardButtonFormat = jsonFormat3(KeyboardButton)
  implicit val replyKeyboardMarkupFormat = jsonFormat3(ReplyKeyboardMarkup)
  implicit val contactFormat = jsonFormat4(Contact)
  implicit val userFormat = jsonFormat4(User)
  implicit val chatFormat = jsonFormat(Chat, "id", "type")
  implicit val replyMessageFormat = jsonFormat6(ReplyMessage)
  implicit val messageFormat = jsonFormat7(Message)
  implicit val updateFormat = jsonFormat2(Update)
  implicit val serverAnswerFormat = jsonFormat2(ServerAnswer)

}
