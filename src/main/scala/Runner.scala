import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import akka.actor.typed.scaladsl.adapter._

//runMain Runner
object Runner extends App {

  val systemName = "bChain"
  val initTO     = 2.seconds

  val commonConfig = ConfigFactory.parseString(
    s"""
       akka {
         actor.provider = cluster
         remote.artery.canonical.hostname = 127.0.0.1

         cluster.jmx.multi-mbeans-in-same-jvm=on
         actor.warn-about-java-serializer-usage=off
       }
    """
  )

  def portConfig(port: Int) =
    ConfigFactory.parseString(s"akka.remote.artery.canonical.port = $port")

  val node1 = akka.actor.typed.ActorSystem(
    Behaviors.setup[Unit] { ctx ⇒
      ctx.log.info("{} started and ready to join cluster", ctx.system.name)

      Behaviors.withTimers[Unit] { timers ⇒
        timers.startSingleTimer("init", (), initTO)

        Behaviors.receive { (ctx, _) ⇒
          Behaviors.same
        }
      }
    },
    systemName,
    portConfig(2550).withFallback(commonConfig).withFallback(ConfigFactory.load())
  )

  val node2 = akka.actor.typed.ActorSystem(
    Behaviors.setup[Unit] { ctx ⇒
      ctx.log.info("{} started and ready to join cluster", ctx.system.name)

      Behaviors.withTimers[Unit] { timers ⇒
        timers.startSingleTimer("init", (), initTO)

        Behaviors.receive { (ctx, _) ⇒
          Behaviors.same
        }
      }
    },
    systemName,
    portConfig(2551).withFallback(commonConfig).withFallback(ConfigFactory.load())
  )

  val node3 = akka.actor.typed.ActorSystem(
    Behaviors.setup[Unit] { ctx ⇒
      ctx.log.info("{} started and ready to join cluster", ctx.system.name)

      Behaviors.withTimers[Unit] { timers ⇒
        timers.startSingleTimer("init", (), initTO)

        Behaviors.receive { (ctx, _) ⇒
          Behaviors.same
        }
      }
    },
    systemName,
    portConfig(2552).withFallback(commonConfig).withFallback(ConfigFactory.load())
  )

  val node1Cluster = Cluster(node1.toClassic)
  val node2Cluster = Cluster(node2.toClassic)
  val node3Cluster = Cluster(node3.toClassic)

  node1Cluster.join(node1Cluster.selfAddress)
  node2Cluster.join(node1Cluster.selfAddress)
  node3Cluster.join(node1Cluster.selfAddress)

  Helpers.waitForAllNodesUp(node1.toClassic, node2.toClassic, node3.toClassic)
  Helpers.wait(20.second)

  node1Cluster.leave(node1Cluster.selfAddress)
  node1.terminate

  node2Cluster.leave(node2Cluster.selfAddress)
  node2.terminate

  node3Cluster.leave(node3Cluster.selfAddress)
  node3.terminate
}
