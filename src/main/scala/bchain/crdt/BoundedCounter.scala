package bchain.crdt

import akka.util.HashCode
import akka.cluster.UniqueAddress
import akka.cluster.ddata.{PNCounter, ReplicatedData, SelfUniqueAddress}

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
  * Replica can decrement its counter, but only up to it's local quota limit. This mean that the `dec` operation may fail, due to insufficient number of
  * resources to spend. In that case we may need to try again on another replica.
  *
  * Replica can transfer part of its own quota (transfer operation) to another replica. Again it cannot share more that it has, so this
  * operation can also potentially fail.
  *
  * Reservation might be a better name 
  */

/** A bounded counter, which enables to perform  Counter-like increment/decrement operation.
  * Unlike `GCounter`/`PNCounter` it's allowed to have a max boundary (hence name),
  * above which any increments will fail to execute.
  */
final case class BoundedCounter(counter: PNCounter, transfers: BoundedCounter.Transfers) extends ReplicatedData { self =>

  //val m = ORMap.empty[String, GSet[String]]
  //val n = ORMap.empty[String, GCounter]

  //licId -> numOfPermissions
  //PNCounterMap.empty[String]

  override type T = BoundedCounter

  //returns quota
  def value(implicit node: SelfUniqueAddress): BigInt =
    self.transfers.list.foldLeft(self.counter.value) { (acc, c) ⇒
      if (c.from == node.uniqueAddress) acc - c.amount
      else if (c.to == node.uniqueAddress) acc + c.amount
      else acc
    }

  def :+(n: BigInt)(implicit node: SelfUniqueAddress): BoundedCounter =
    BoundedCounter(self.counter :+ n, transfers)

  def :-(n: BigInt)(implicit node: SelfUniqueAddress): BoundedCounter =
    BoundedCounter(self.counter decrement n, transfers)

  override def merge(that: BoundedCounter): BoundedCounter = {
    val a = self.counter.merge(that.counter)

    BoundedCounter(a, ???)
  }

  //
  override def toString: String = s"BCounter(${counter.value})"

  override def equals(o: Any): Boolean = o match {
    case other: BoundedCounter ⇒
      self.counter.equals(other.counter) && transfers == other.transfers
    case _ ⇒ false
  }

  override def hashCode: Int = {
    var result = HashCode.SEED
    result = HashCode.hash(result, counter.hashCode)
    result = HashCode.hash(result, transfers)
    result
  }
}
