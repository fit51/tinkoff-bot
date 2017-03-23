package com.bankbot

import akka.http.scaladsl.model.ResponseEntity

/**
  * Created by pavel on 22.03.17.
  */
object CommonTypes {

  case class ResponceCodeException(code: String, entity: ResponseEntity) extends Exception("Responce code: " + code)

}
