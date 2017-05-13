package com.bankbot

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import scala.concurrent.duration._
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, KillSwitches, Supervision}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.bankbot.CommonTypes.ResponceCodeException
import com.bankbot.telegram.{MessageMarshallingTelegram, TelegramApi, TelegramKey}
import com.bankbot.telegram.PrettyMessage.{prettyHelp, prettyNonPrivate}
import spray.json.JsArray
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import telegram.TelegramTypes.{Message, ServerAnswer, Update}

/**
  * Actor that gets Telegram Updates
  */

object TelegramUpdater {
  def props(sessionManager: ActorRef, noSessionActions: ActorRef, telegramApi: TelegramApi) =
    Props(classOf[TelegramUpdater], sessionManager, noSessionActions, telegramApi)
  val name = "telegram-updater"
  case object getOffset
  case class Offset(offset: Int)
  case object StartProcessing
  case object StopProcessing
  case object StartStream
  case object StreamStopped

}

class TelegramUpdater(sessionManager: ActorRef, noSessionActions: ActorRef,
                      telegramApi: TelegramApi)
  extends Actor with ActorLogging with TelegramKey with MessageMarshallingTelegram {
  import TelegramUpdater._
  implicit val logger: LoggingAdapter = log

  log.info("TelegramUpdater created! " + context.self.path.toString)

  private var offset = 0

  def init: Receive = {
//    case StartProcessing => {
//      log.info("TelegramUpdater starts processing messages! " + context.self.path.toString)
//      context.become(work)
//      telegramApi.getUpdates(offset)
//    }
    case StartProcessing => {
      //should use long polling, so ConnectionPool is not applicable
      context.become(work)
      val url = "https://api.telegram.org/bot" + token
      val http = Http(context.system)
      val params = Map("offset" -> offset.toString)
      val uri = Uri(url + "/getUpdates").withQuery(Query(params))
      //    val httpClient = http.superPool[NotUsed]()
      val httpClient = http.outgoingConnectionHttps("api.telegram.org")
      val decider : Supervision.Decider = {
        case _: ResponceCodeException => Supervision.Resume
        case _                    => Supervision.Stop
      }
      implicit val materializer = ActorMaterializer(
        ActorMaterializerSettings(context.system)
          .withSupervisionStrategy(decider)
      )
      import context.dispatcher
      val source = Source.tick(100 millis, 1 seconds, HttpRequest(uri = uri))
        .via(httpClient)
      val handleResponce: Flow[HttpResponse, Update, NotUsed] =
        Flow[HttpResponse].mapAsync(50) { responce =>
          responce match {
            case HttpResponse(StatusCodes.OK, _, entity, _) => {
              ((Unmarshal(entity).to[ServerAnswer]).recover {
                case _ => {
                  logger.warning("Telegram getUpdates Responce Unmarshall Failed")
                  ServerAnswer(false, Array())
                }
              })
            }
            case HttpResponse(code, _, entity, _) => {
              logger.warning("Telegram getUpdates Request failed, response code: " + code)
              throw ResponceCodeException("Telegram getUpdates Responce code:", entity)
            }
          }
        }
          .mapConcat[Update] {
          serverAnswer => serverAnswer.result.toList
        }

      val sink: Sink[Update, NotUsed]= Sink.actorRef(self, StreamStopped)

      val (killSwitch, done) = ((source.viaMat(KillSwitches.single)(Keep.right)
        via handleResponce).toMat(sink)(Keep.both)).run()
      log.info(killSwitch.toString())
      log.info(done.toString())
//      killSwitch.shutdown()
    }
  }

  def work: Receive = {
    case StreamStopped => {
      logger.info("Stream Stopped")
    }
    case StopProcessing => {
      log.info("TelegramUpdater stops processing messages! " + context.self.path.toString)
      context.become(init)
    }
    case ServerAnswer(true, result) => {
      for (update <- result) {
        if (update.message.chat.c_type == "private") {
          update.message match {
            case Message(_, Some(user), chat, _, None, Some(text), None) => {
              text match {
                case s if s == "/rates" || s == "/r" => {
                  noSessionActions ! NoSessionActions.SendRates(chat.id)
                }
                case s if s == "/balance" || s == "/b" => {
                  sessionManager ! UserSession.BalanceCommand(user, chat.id)
                }
                case s if s == "/history" || s == "/hi" => {
                  sessionManager ! UserSession.HistoryCommand(user, chat.id)
                }
                case s if s == "/help" || s == "/h" => {
                  noSessionActions ! NoSessionActions.SendMessage(chat.id, prettyHelp)
                }
                case s if s == "/start" || s == "/s" => {
                  noSessionActions ! NoSessionActions.SendMessage(chat.id, prettyHelp)
                }
                case s => {
                  noSessionActions ! NoSessionActions.SendMessage(chat.id, "No Such Command\nSee /help")
                }
              }
            }
            case Message(_, _, _, _, _, _, Some(contact)) => {
              sessionManager ! SessionManager.PossibleContact(update.message.chat.id, contact)
            }
            case Message(message_id, Some(user), chat, _, Some(reply), Some(text), None) => {
              sessionManager ! SessionManager.PossibleReply(chat.id, reply.message_id, text)
            }
          }
        } else {
          noSessionActions ! NoSessionActions.SendMessage(update.message.chat.id, prettyNonPrivate)
        }
        offset = update.update_id + 1
      }
      telegramApi.getUpdates(offset)
    }
    case update: Update => {
      if (update.message.chat.c_type == "private") {
        update.message match {
          case Message(_, Some(user), chat, _, None, Some(text), None) => {
            text match {
              case s if s == "/rates" || s == "/r" => {
                noSessionActions ! NoSessionActions.SendRates(chat.id)
              }
              case s if s == "/balance" || s == "/b" => {
                sessionManager ! UserSession.BalanceCommand(user, chat.id)
              }
              case s if s == "/history" || s == "/hi" => {
                sessionManager ! UserSession.HistoryCommand(user, chat.id)
              }
              case s if s == "/help" || s == "/h" => {
                noSessionActions ! NoSessionActions.SendMessage(chat.id, prettyHelp)
              }
              case s if s == "/start" || s == "/s" => {
                noSessionActions ! NoSessionActions.SendMessage(chat.id, prettyHelp)
              }
              case s => {
                noSessionActions ! NoSessionActions.SendMessage(chat.id, "No Such Command\nSee /help")
              }
            }
          }
          case Message(_, _, _, _, _, _, Some(contact)) => {
            sessionManager ! SessionManager.PossibleContact(update.message.chat.id, contact)
          }
          case Message(message_id, Some(user), chat, _, Some(reply), Some(text), None) => {
            sessionManager ! SessionManager.PossibleReply(chat.id, reply.message_id, text)
          }
        }
      } else {
        noSessionActions ! NoSessionActions.SendMessage(update.message.chat.id, prettyNonPrivate)
      }
      offset = update.update_id + 1
    }
    case ServerAnswer(false, _) => telegramApi.getUpdates(offset)
    case getOffset => sender ! Offset(offset)
  }

  def receive = init

}
