package com.bankbot

import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.cluster.{Cluster, MemberStatus}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.event.Logging
import akka.routing.FromConfig
import com.bankbot.ClusterDomainEventListener.ControlUpdater
import com.bankbot.TelegramUpdater.{StartProcessing, StopProcessing}
import com.bankbot.telegram.TelegramApiImpl
import com.typesafe.config.ConfigFactory

/**
  * Main class
  */
object Main extends App {

  if(args.isEmpty)
    startSingletone()
  else
    args(0) match {
      case "master" => startMaster()
      case "worker" => startWorker()
    }

  def startSingletone() = {
    val conf = ConfigFactory.load()
    implicit val system = ActorSystem("BotSystem")

    val telegramApi = new TelegramApiImpl
    val noSessionRouter = system.actorOf(
      FromConfig.props(NoSessionActions.props(
        conf.getInt("app.updateRatesInterval"))),
      "nosessionrouter"
    )
    val sessionRouter = system.actorOf(
      FromConfig.props(SessionManager.props()),
      "sessionrouter"
    )

    val getUpdates = system.actorOf(TelegramUpdater.props(sessionRouter, noSessionRouter, telegramApi))
    getUpdates ! StartProcessing
  }

  def startWorker() = {
    val conf = ConfigFactory.load("cluster_worker")
    implicit val system = ActorSystem("botsystem", conf)

    println(s"Starting node with roles: ${Cluster(system).selfRoles}")
  }

  def startMaster() = {
    val conf = ConfigFactory.load("cluster_main")
    implicit val system = ActorSystem("botsystem", conf)
    val log = Logging.getLogger(system, this)

    log.info(s"Starting node with roles: ${Cluster(system).selfRoles}, waiting for 2 worker nodes!")


    val noSessionRouter = system.actorOf(
      FromConfig.props(NoSessionActions.props(
        conf.getInt("app.updateRatesInterval"))),
      "nosessionrouter"
    )
    val sessionRouter = system.actorOf(
      FromConfig.props(SessionManager.props()),
      "sessionrouter"
    )

    val telegramApi = new TelegramApiImpl

    val updaterProps = Props(new TelegramUpdater(sessionRouter, noSessionRouter, telegramApi) {
      override def preStart(): Unit = {
        val numberOfworkers = Cluster(context.system).state.members.foldLeft(0)((acc, m)=>
          if(m.roles.contains("worker") && m.status == MemberStatus.up)
            acc+1
          else
            acc
        )
        if(numberOfworkers >= 2)
          self ! StartProcessing
      }
    })

    val singletonManager = system.actorOf(
      ClusterSingletonManager.props(
        updaterProps,
        PoisonPill,
        ClusterSingletonManagerSettings(system)
          .withRole("master")
          .withSingletonName(TelegramUpdater.name)
      )
    )

    val telegramUpdater = system.actorOf(
      ClusterSingletonProxy.props(
        singletonManager.path.child(TelegramUpdater.name)
          .toStringWithoutAddress,
        ClusterSingletonProxySettings(system)
          .withRole("master")
          .withSingletonName("updater-proxy"))
    )

    val clusterListener = system.actorOf(Props[ClusterDomainEventListener], "clusterListener")
    clusterListener ! ControlUpdater(telegramUpdater)

    val cluster = Cluster(system)
    cluster.registerOnMemberUp {
      log.info("Woker NodesBot Functionality Starts!")
      telegramUpdater ! StartProcessing
    }

    cluster.registerOnMemberRemoved {
      log.info("Bot Functionality Stops, Waiting for 2 worker nodes!")
      telegramUpdater ! StopProcessing
    }
  }
}
