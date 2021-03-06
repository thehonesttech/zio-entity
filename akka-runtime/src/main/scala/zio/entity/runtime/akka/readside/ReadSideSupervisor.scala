package zio.entity.runtime.akka.readside

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.cluster.sharding.ShardRegion
import zio.entity.runtime.akka.readside.ReadSideSupervisor.{GracefulShutdown, ShutdownCompleted, Tick}
import zio.entity.runtime.akka.readside.ReadSideWorkerActor.KeepRunningWithWorker

import scala.concurrent.duration.{FiniteDuration, _}

object ReadSideSupervisor {

  private final case object Tick

  final case object GracefulShutdown

  final case object ShutdownCompleted

  def props(processCount: Int, shardRegion: ActorRef, heartbeatInterval: FiniteDuration): Props =
    Props(new ReadSideSupervisor(processCount, shardRegion, heartbeatInterval))
}

final class ReadSideSupervisor(processCount: Int, shardRegion: ActorRef, heartbeatInterval: FiniteDuration) extends Actor with ActorLogging {

  import context.dispatcher

  // TODO: try to use ZIO clock
  private val heartbeat =
    context.system.scheduler.scheduleWithFixedDelay(0.seconds, heartbeatInterval, self, Tick)

  context.watch(shardRegion)

  override def postStop(): Unit = {
    heartbeat.cancel()
    ()
  }

  override def receive: Receive = {
    case Tick =>
      (0 until processCount).foreach { processId =>
        shardRegion ! KeepRunningWithWorker(processId) //KeepRunning(processId)
      }
    case Terminated(`shardRegion`) =>
      context.stop(self)
    case GracefulShutdown =>
      log.info(s"Performing graceful shutdown of [$shardRegion]")
      shardRegion ! ShardRegion.GracefulShutdown
      val replyTo = sender()
      context.become { case Terminated(`shardRegion`) =>
        log.info(s"Graceful shutdown completed for [$shardRegion]")
        context.stop(self)
        replyTo ! ShutdownCompleted
      }

  }
}
