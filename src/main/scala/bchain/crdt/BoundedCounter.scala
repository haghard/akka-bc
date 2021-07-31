package bchain.crdt

import akka.cluster.UniqueAddress
import akka.cluster.ddata.{GCounter, GSet, LWWRegister, ORMap, PNCounter, PNCounterMap, ReplicatedData, SelfUniqueAddress}
import akka.util.HashCode

//https://bartoszsypytkowski.com/state-based-crdts-bounded-counter/

//https://github.com/Horusiath/crdt-examples/blob/master/Crdt/convergent/BCounter.fs
object BoundedCounter {

  final case class Transfer(from: UniqueAddress, to: UniqueAddress, amount: BigInt)
  final case class Transfers(list: Vector[Transfer])
}

/** Each replica has its own part of the total quota to consume.
  *
  * Replica can increment (inc operation) its own quota as it sees fit, therefore increasing a total quota available. This is safe operation,
  * as we don't risk our counter to run below 0 this way.
  *
  * Replica can decrement its counter, but only up to it's local quota limit. This mean that our dec may fail, due to insufficient number of
  * resources to spend. In that case we may need to try again on another replica.
  *
  * Replica can transfer part of its own quota (transfer operation) to another replica. Again it cannot share more that it has, so this
  * operation can also potentially fail.
  */

/** A bounded counter, which enables to perform  Counter-like increment/decrement operation.
  * Unlike `GCounter`/`PNCounter` it's allowed to have a max boundary (hence name),
  * above which any increments will fail to execute.
  */
final case class BoundedCounter(counter: PNCounter, transfers: BoundedCounter.Transfers) extends ReplicatedData {

  //val m = ORMap.empty[String, GSet[String]]
  //val n = ORMap.empty[String, GCounter]

  //licId -> numOfPermissions
  //PNCounterMap.empty[String]

  override type T = BoundedCounter

  //returns quota
  def value(implicit node: SelfUniqueAddress): BigInt =
    transfers.list.foldLeft(counter.value) { (acc, c) ⇒
      if (c.from == node.uniqueAddress) acc - c.amount
      else if (c.to == node.uniqueAddress) acc + c.amount
      else acc
    }

  def :+(n: BigInt)(implicit node: SelfUniqueAddress): BoundedCounter =
    BoundedCounter(counter :+ n, transfers)

  def :-(n: BigInt)(implicit node: SelfUniqueAddress): BoundedCounter =
    BoundedCounter(counter decrement n, transfers)

  override def merge(that: BoundedCounter): BoundedCounter = {
    val a = counter.merge(that.counter)

    BoundedCounter(a, ???)
  }

  //
  override def toString: String = s"BoundedCounter(${counter.value})"

  override def equals(o: Any): Boolean = o match {
    case other: BoundedCounter ⇒
      counter.equals(other.counter) && transfers == other.transfers
    case _ ⇒ false
  }

  override def hashCode: Int = {
    var result = HashCode.SEED
    result = HashCode.hash(result, counter.hashCode)
    result = HashCode.hash(result, transfers)
    result
  }
}
