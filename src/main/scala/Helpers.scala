import java.util.concurrent.ThreadLocalRandom

import akka.actor.ActorSystem
import akka.cluster.{Cluster, MemberStatus}
import spray.json.{JsNumber, JsObject, JsString}

import scala.concurrent.duration.FiniteDuration

object Helpers {

  /** Block the calling thread until all the given nodes have reached Up and that is seen from all nodes. In a real
    * system (tm) you should have no use for such a thing but instead use an actor listening for cluster membership
    * events. This is here to keep samples brief and easy to follow.
    */
  def waitForAllNodesUp(nodes: ActorSystem*): Unit =
    if (nodes.exists(node ⇒ Cluster(node).state.members.count(_.status == MemberStatus.Up) != nodes.size)) {
      Thread.sleep(250)
      waitForAllNodesUp(nodes: _*)
    } else ()

  /** Block the calling thread for the given time period. In real system (tm) built with Akka you should never have
    * anything like this - it is just for making the samples easier to follow and understand.
    */
  def wait(d: FiniteDuration): Unit =
    Thread.sleep(d.toMillis)

  def genName = {
    val sb = new StringBuffer()
    (0 to 8).foreach { _ ⇒
      sb.append(ThreadLocalRandom.current.nextInt(65, 85).toChar)
    }
    sb.toString
  }

  def jsonTransaction() =
    JsObject(
      "from"   → JsString(genName),
      "to"     → JsString(genName),
      "amount" → JsNumber(ThreadLocalRandom.current.nextDouble()),
      "sign"   → JsString(ThreadLocalRandom.current.nextLong.toString)
    )

  import bchain.domain.v1.{KeyVal, ObjVal, Val}
  def pbTransaction: Val =
    /*val kvs = ObjVal(
      Seq(
        KeyVal("keyLong", Some(Val(Val.Union.LongT(3L)))),
        KeyVal("keyStr", Some(Val(Val.Union.StringT("hello"))))
      )
    )

    Val(
      Val.Union.ArrayT(
        ArrVal(
          Seq(
            Val(Val.Union.LongT(1L)),
            Val(Val.Union.StringT("hi")),
            Val(Val.Union.LongT(3L)),
            Val(Val.Union.ObjectT(kvs))
          )
        )
      )
    )*/

    Val(
      Val.Union.ObjectT(
        ObjVal(
          Vector(
            KeyVal("from", Some(Val(Val.Union.StringT(genName)))),
            KeyVal("to", Some(Val(Val.Union.StringT(genName)))),
            KeyVal("amount", Some(Val(Val.Union.DoubleT(ThreadLocalRandom.current.nextDouble())))),
            KeyVal("sign", Some(Val(Val.Union.StringT(ThreadLocalRandom.current.nextLong.toString))))
          )
        )
      )
    )

}
