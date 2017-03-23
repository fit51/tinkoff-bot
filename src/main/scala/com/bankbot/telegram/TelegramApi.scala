package com.bankbot.telegram

import akka.event.LoggingAdapter
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.bankbot.CommonTypes._
import com.bankbot.Main

import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * Class that handles httpRequests to Telegram Api
  * @constructor creates a new Api class for Actor
  */
class TelegramApi(http: HttpExt, materializer: ActorMaterializer, log: LoggingAdapter,
                  dispatcher: ExecutionContextExecutor) extends MessageMarshallingTelegram {
  implicit val ec = dispatcher
  final implicit val mat = materializer

  val url = "https://api.telegram.org/bot" + Main.token

  def getUpdates(offset: Int): Future[HttpResponse] = {
    val params = Map("offset" -> offset.toString)
    val uri = Uri(url + "/getUpdates").withQuery(Query(params))
    http.singleRequest(HttpRequest(uri = uri))(materializer)
  }

  def sendMessage(params: Map[String, String]): Future[HttpEntity] = {
    val uri = Uri(url + "/sendMessage").withQuery(Query(params))
    val response = http.singleRequest(HttpRequest(uri = uri))(materializer)
    response map {
      case HttpResponse(StatusCodes.OK, _, entity, _) => {
        log.info("Telegram sendMessage Request Success")
        entity
      }
      case HttpResponse(code, _, entity, _) => {
        log.info("Telegram sendMessage Request failed, response code: " + code)
        throw ResponceCodeException("Tinkoff Responce code:", entity)
      }
    }
  }

}
