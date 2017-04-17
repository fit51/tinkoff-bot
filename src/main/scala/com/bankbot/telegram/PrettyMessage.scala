package com.bankbot.telegram

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.model.DateTime
import com.bankbot.tinkoff.TinkoffTypes._

/**
  * Object provides methods to Create Strings for Telegram Messages with HTML parse_mode
  */

object PrettyMessage {

  val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  def prettyRates(last_update: Instant, rates: Vector[Rate], zoneId: ZoneId) = {
    if(rates.isEmpty)
      "Rates are not updated yet"
    else
    s"LastUpdate:\t${last_update.atZone(zoneId).format(format)}\n" +
    "<b>From</b>\t<b>To</b>\t<b>Sell</b>\t<b>Buy</b>\n" +
      (rates.map { rate =>
      rate.fromCurrency.name + "\t"+ rate.toCurrency.name + "\t" +rate.sell + "\t" + rate.buy + "\n"
      }).mkString("")
  }

  def prettyHelp() =
    """<b>Hi there!</b>
      |I am a bot that implements some functionality of Tinkoff Internet Bank.
      |You can control me with this commands:
      |/rates - get Currency Rates
      |/balance - get your Balance
      |/history - get your 10 last operations
      |/help - see this message again
      |Note that balance and history require sharing your phone number.
    """.stripMargin

  def prettyNonPrivate() =
    """<b>Sorry!</b>
      |This bot support only <b>private</b> chats.
    """.stripMargin

  def prettyThx4Contact() =
    """Thanks for sharing your contact!
      |Now you can use this commands:
      |/balance - get your Balance
      |/history - get yor history
    """.stripMargin

  def prettyBalance(name: String, accountType: String, accountBalance: Balance) = {
    s"<b>$name</b>\n" +
    s"<b>Type:</b> $accountType\n" +
      s"<b>Balance:</b>\n ${accountBalance.value}\t${accountBalance.currency.name}"
  }

  def prettyOperation(description: String, debitingTime: DebitingTime, amount: Amount,
                      spendingCategory: SpendingCategory) = {
    s"<b>$description</b>\n" +
    s"<b>Debiting time:</b> ${DateTime(debitingTime.milliseconds).toString().dropRight(9)}\n" +
    s"<b>Amount:</b> ${amount.value}\n" +
    s"<b>${spendingCategory.name}</b>\n"
  }

}
