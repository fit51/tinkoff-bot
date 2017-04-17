package com.bankbot.telegram

import spray.json._

/**
  * Case classes used in Telegram Api
  */

object TelegramTypes {

  case class TelegramMessage(chat_id: Int, text: String, parse_mode: String = "HTML",
                         reply_markup: Option[Either[ForceReply, ReplyKeyboardMarkup]] = None)

  case class ReplyKeyboardMarkup(keyboard: Vector[Vector[KeyboardButton]], resize_keyboard: Option[Boolean] = None,
                                 one_time_keyboard: Option[Boolean] = None)

  case class ForceReply(force_reply: Boolean)

  case class KeyboardButton(text: String, request_contact: Option[Boolean] = None,
                            request_location: Option[Boolean] = None)

  case class Contact(phone_number: String, first_name: String, last_name: Option[String], user_id: Int)

  case class User(id: Int, first_name: String, last_name: Option[String], username: Option[String])

  case class Chat(id: Int, c_type: String)

  case class ReplyMessage(message_id: Int, from: Option[User], chat: Chat, date: Int,
                          text: Option[String], contact: Option[Contact])

  case class Message(message_id: Int, from: Option[User], chat: Chat, date: Int, reply_to_message: Option[ReplyMessage],
                     text: Option[String], contact: Option[Contact])

  case class Update(update_id: Int, message: Message)

  case class ServerAnswer(ok: Boolean, result: Array[Update])

  case class ServerAnswerReply(ok: Boolean, result: ReplyMessage)

}

/**
  * Json Marshalling for case classes used in Telegram Api
  */

trait MessageMarshallingTelegram extends DefaultJsonProtocol {

  import TelegramTypes._

  implicit val forceReplyFormat = jsonFormat1(ForceReply)
  implicit val keyboardButtonFormat = jsonFormat3(KeyboardButton)
  implicit val replyKeyboardMarkupFormat = jsonFormat3(ReplyKeyboardMarkup)
  implicit val telegramMessageFormat = jsonFormat4(TelegramMessage)
  implicit val contactFormat = jsonFormat4(Contact)
  implicit val userFormat = jsonFormat4(User)
  implicit val chatFormat = jsonFormat(Chat, "id", "type")
  implicit val replyMessageFormat = jsonFormat6(ReplyMessage)
  implicit val messageFormat = jsonFormat7(Message)
  implicit val updateFormat = jsonFormat2(Update)
  implicit val serverAnswerFormat = jsonFormat2(ServerAnswer)
  implicit val serverAnswerReplyFormat = jsonFormat2(ServerAnswerReply)

}
