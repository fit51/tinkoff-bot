package com.bankbot

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.{Cluster, Member, MemberStatus}
import akka.cluster.ClusterEvent._
import com.bankbot.ClusterDomainEventListener.ControlUpdater
import com.bankbot.TelegramUpdater.{StartProcessing, StopProcessing}

/**
  * Actor that is responsible for cluster events handling
  */
object ClusterDomainEventListener {
  case class ControlUpdater(telegramUpdater: ActorRef)
}

class ClusterDomainEventListener extends Actor with ActorLogging {
  Cluster(context.system).subscribe(self, classOf[ClusterDomainEvent])
  var updater: Option[ActorRef] = None
  var upWorkers = Set[Member]()

  def receive = {
    case MemberJoined(member) =>
      log.info(s"$member JOINED.")
    case MemberUp(member) => {
      log.info(s"$member UP.")
      if (member.roles.contains("worker")) {
        upWorkers = upWorkers + member
        if(upWorkers.size == 2) updater foreach(_ ! StartProcessing)
      }
    }
    case MemberExited(member) =>
      log.info(s"$member EXITED.")
    case MemberRemoved(member, previousState) =>
      if (previousState == MemberStatus.Exiting) {
        log.info(s"Member $member Previously gracefully exited, REMOVED.")
      } else {
        log.info(s"$member Previously downed after unreachable, REMOVED.")
      }
    case UnreachableMember(member) => {
      log.info(s"$member UNREACHABLE")
      if (member.roles.contains("worker")) {
        upWorkers = upWorkers - member
        if(upWorkers.size ==  1) updater foreach(_ ! StopProcessing)
      }
    }
    case ReachableMember(member) => {
      log.info(s"$member REACHABLE")
      if (member.roles.contains("worker")) {
        upWorkers = upWorkers + member
        if(upWorkers.size ==  2) updater foreach(_ ! StartProcessing)
      }
    }
    case state: CurrentClusterState => {
      log.info(s"Current state of the cluster: $state")
      upWorkers = upWorkers ++ state.members.filter(m =>
        m.roles.contains("worker") && m.status == MemberStatus.up
      )
    }

    case ControlUpdater(telegramUpdater) => updater = Some(telegramUpdater)
  }
}
