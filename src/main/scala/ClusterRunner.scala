import actors.ClusterEventsListener
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.typed.{Cluster, Down, Join, JoinSeedNodes, Leave, SelfUp, Unsubscribe}

import java.util.concurrent.{CountDownLatch, TimeUnit}

//runMain ClusterRunner
object ClusterRunner extends App {

  val systemName = "ledger"
  val initTO     = 2.seconds
  val name       = "cl-Events"

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

  val node1 = akka.actor.typed.ActorSystem[Nothing](
    Behaviors
      .setup[SelfUp] { ctx ⇒
        val cluster = Cluster(ctx.system)
        cluster.subscriptions ! akka.cluster.typed.Subscribe(ctx.self, classOf[SelfUp])
        /*Behaviors
          .withTimers[Unit] { timers ⇒
            timers.startSingleTimer("init", (), initTO)
            Behaviors.receive { (ctx, _) ⇒
              Behaviors.same
            }
          }
          .narrow*/
        //

        //val bs = cats.kernel.BoundedSemilattice[Set[Int]]
        //cats.kernel.BoundedSemilattice.instance[Int](0, { _ + _ })

        Behaviors.receiveMessage { case SelfUp(state) ⇒
          //state.members.mkString(",")
          /*ctx.log.warn(
            "Leader {} UP {}",
            state.leader.getOrElse(cluster.selfMember.address),
            cluster.selfMember.uniqueAddress
          )*/
          cluster.subscriptions ! Unsubscribe(ctx.self)
          ctx.spawn(ClusterEventsListener(2550), name)
          Behaviors.same
        }
      }
      .narrow,
    systemName,
    portConfig(2550).withFallback(commonConfig).withFallback(ConfigFactory.load())
  )

  val node2 = akka.actor.typed.ActorSystem[Nothing](
    Behaviors
      .setup[SelfUp] { ctx ⇒
        val cluster = Cluster(ctx.system)
        cluster.subscriptions ! akka.cluster.typed.Subscribe(ctx.self, classOf[SelfUp])

        Behaviors.receiveMessage { case SelfUp(state) ⇒
          /*ctx.log.warn(
            "Leader {} UP {}",
            state.leader.getOrElse(cluster.selfMember.address),
            cluster.selfMember.uniqueAddress
          )*/
          cluster.subscriptions ! Unsubscribe(ctx.self)
          ctx.spawn(ClusterEventsListener(2551), name)
          Behaviors.same
        }
      }
      .narrow,
    systemName,
    portConfig(2551).withFallback(commonConfig).withFallback(ConfigFactory.load())
  )

  val node3 = akka.actor.typed.ActorSystem[Nothing](
    Behaviors
      .setup[SelfUp] { ctx ⇒
        val cluster = Cluster(ctx.system)
        cluster.subscriptions ! akka.cluster.typed.Subscribe(ctx.self, classOf[SelfUp])

        /*Behaviors
        .withTimers[Unit] { timers ⇒
          timers.startSingleTimer("init", (), initTO)

          Behaviors.receive { (ctx, _) ⇒
            Behaviors.same
          }
        }
        .narrow*/
        Behaviors.receiveMessage { case SelfUp(state) ⇒
          //state.members.mkString(",")
          ctx.log.warn(
            "Leader {} UP {}",
            state.leader.getOrElse(cluster.selfMember.address),
            cluster.selfMember.uniqueAddress
          )
          cluster.subscriptions ! Unsubscribe(ctx.self)
          ctx.spawn(ClusterEventsListener(2552), name)
          Behaviors.same
        }
      }
      .narrow,
    systemName,
    portConfig(2552).withFallback(commonConfig).withFallback(ConfigFactory.load())
  )

  val to = 5.seconds

  val joinCmd = JoinSeedNodes(
    Seq(Cluster(node1).selfMember.address, Cluster(node2).selfMember.address, Cluster(node3).selfMember.address)
  )

  Cluster(node1).manager.tell(joinCmd /*Join(seed)*/ )
  Cluster(node2).manager.tell(joinCmd)
  Cluster(node3).manager.tell(joinCmd)

  Helpers.waitForAllNodesUp(node1.toClassic, node2.toClassic, node3.toClassic)
  Helpers.wait(10.second)

  val isCompleted = new CountDownLatch(1)
  implicit val ec = scala.concurrent.ExecutionContext.global

  //rely on SBR to terminate members
  node3.terminate() //or Cluster(node1).manager.tell(Leave(Cluster(node3).selfMember.address)) for graceful exit
  node3.whenTerminated.onComplete { r ⇒
    println(s"Down node3: $r")
    node2.terminate()
    node2.whenTerminated.onComplete { r ⇒
      println(s"Down node2: $r")
      node1.terminate()
      node1.whenTerminated.onComplete { r ⇒
        println(s"Down node1: $r")
        isCompleted.countDown()
        println("★ ★ ★ Exit ★ ★ ★")
      }
    }
  }

  isCompleted.await(8, TimeUnit.SECONDS)
}
