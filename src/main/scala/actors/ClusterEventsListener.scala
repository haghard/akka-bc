package actors

import akka.actor.typed.Behavior
import akka.cluster.ClusterEvent
import akka.cluster.ClusterEvent._
import akka.cluster.typed.Cluster
import akka.cluster.typed.Subscribe
import akka.actor.typed.scaladsl.Behaviors

object ClusterEventsListener {

  def apply(port: Int): Behavior[ClusterEvent.ClusterDomainEvent] =
    Behaviors.setup { ctx ⇒
      Cluster(ctx.system).subscriptions ! Subscribe(ctx.self, classOf[ClusterEvent.ClusterDomainEvent])

      Behaviors.receiveMessage /*Partial*/ {
        case MemberUp(member) ⇒
          ctx.log.warn("{}  Member is Up: {}", port, member.address)
          Behaviors.same
        case UnreachableMember(member) ⇒
          ctx.log.warn("{}  Member detected as unreachable: {}", port, member)
          Behaviors.same
        case MemberRemoved(member, previousStatus) ⇒
          ctx.log.warn("{}  Member is Removed: {} after {}", port, member.address, previousStatus)
          Behaviors.same
        case LeaderChanged(member) ⇒
          ctx.log.warn("{}  Leader changed: {}", port, member)
          Behaviors.same
        /*case any: MemberEvent ⇒
          ctx.log.warn("{}  Member Event: {}", port, any.toString)
          Behaviors.same*/
        case any: ClusterDomainEvent ⇒
          ctx.log.warn("{}  Member Event: {}", port, any.toString)
          Behaviors.same
      }
    }
}
