package akka.cluster.crdt

import akka.cluster.UniqueAddress
import akka.actor.AddressFromURIString

import scala.annotation.nowarn
import akka.cluster.ddata.{RemovedNodePruning, ReplicatedData, ReplicatedDataSerialization}
import akka.util.HashCode
import bchain.{Block, BlockChain}

/** https://www.geeksforgeeks.org/merge-two-sorted-arrays/
  * https://github.com/haghard/dr-chatter/blob/f60e3174f6f58afc824fb5c47109f7a1bdf0daff/src/main/scala/chatter/crdt/ChatTimeline.scala#L30
  * https://github.com/typelevel/algebra/blob/46722cd4aa4b01533bdd01f621c0f697a3b11040/docs/docs/main/tut/typeclasses/overview.md
  */
object ReplicatedBChain {
  private val Sep = "_"

  def toStr(node: UniqueAddress): String =
    s"${node.address}$Sep${node.longUid}"

  def fromStr(s: String): UniqueAddress = {
    val segments = s.split(Sep)
    UniqueAddress(AddressFromURIString(segments(0)), segments(1).toLong)
  }

  def apply(blockChain: BlockChain): ReplicatedBChain =
    ReplicatedBChain(blockChain, akka.cluster.VectorClock())
}

final case class ReplicatedBChain private (
  blockChain: BlockChain,
  clock: akka.cluster.VectorClock // = akka.cluster.VectorClock()
  // versions: VersionVectors[Address] = VersionVectors.empty[Address](Member.addressOrdering)
) extends ReplicatedData
    with RemovedNodePruning
    with ReplicatedDataSerialization { self ⇒

  override type T = ReplicatedBChain

  def :+(block: Block, node: UniqueAddress): ReplicatedBChain = {
    val (added, updatedChain) = self.blockChain + block
    if (added) self.copy(updatedChain, self.clock.:+(ReplicatedBChain.toStr(node)))
    else self
  }

  /*@nowarn
  private def merge2(candidateA: List[Block], candidateB: List[Block]): List[Block] = {
    @scala.annotation.tailrec
    def divergedIndex(a: List[Block], b: List[Block], limit: Int, i: Int = 0): Option[Int] =
      if (i < limit)
        if (a(i) != b(i)) Some(i) else divergedIndex(a, b, limit, i + 1)
      else None

    divergedIndex(candidateA, candidateB, math.min(candidateA.length, candidateB.length)) match {
      case Some(i) ⇒
        val (same, a) = candidateA.splitAt(i)
        val (_, b)    = candidateB.splitAt(i)
        var iA        = a.length - 1
        var iB        = b.length - 1

        var mergeResult = Vector.fill[Block](a.length + b.length)(null)
        var limit       = mergeResult.length
        while (limit > 0) {
          limit -= 1
          val elem = if (iB < 0 || (iA >= 0 && a(iA).ts >= b(iB).ts)) { //seqNum
            iA -= 1
            a(iA + 1)
          } else {
            iB -= 1
            b(iB + 1)
          }
          mergeResult = mergeResult.updated(limit, elem)
        }
        same ++ mergeResult
      case None ⇒
        if (candidateA.size > candidateB.size) candidateA else candidateB
    }
  }*/

  /*
    Requires a bounded semilattice (or idempotent commutative monoid).
    Monotonic semi-lattice + merge = Least Upper Bound

    We rely on commutativity to ensure that machine A merging with machine B yields the same result as machine B merging with machine A.
    We need associativity to ensure we obtain the correct result when three or more machines are merging data.
    We need an identity element to initialise empty ReplicatedChain.
    Finally, we need idempotency, to ensure that if two machines hold the same data
    in a per-machine ReplicatedChain, merging them will not lead to an incorrect result.
   */
  override def merge(that: ReplicatedBChain): ReplicatedBChain =
    if (self.clock < that.clock)
      that
    else if (self.clock > that.clock)
      self
    else if (self.clock <> that.clock) {
      // val blocks = merge2(blockChain.chain, that.blockChain.chain)
      // ReplicatedChain(blockChain.fromBlocks(blocks), versions merge that.versions)

      // The longest chain wins
      val winner =
        if (self.blockChain.size == that.blockChain.size)
          // (earliest write wins) The youngest chain wins
          if (self.blockChain.latest.ts < that.blockChain.latest.ts) self else that
        else if (self.blockChain.size > that.blockChain.size) self
        else that

      ReplicatedBChain(winner.blockChain, self.clock merge that.clock)
    } else self //  ==

  override def hashCode: Int = {
    var result = HashCode.SEED
    result = HashCode.hash(result, self.blockChain)
    result = HashCode.hash(result, self.clock)
    result
  }

  override def equals(o: Any): Boolean = o match {
    case other: ReplicatedBChain ⇒ self.blockChain == other.blockChain && self.clock == other.clock
    case _                       ⇒ false
  }

  override def modifiedByNodes: Set[UniqueAddress] =
    clock.versions.keySet.map(ReplicatedBChain.fromStr)

  override def needPruningFrom(removedNode: UniqueAddress): Boolean =
    clock.versions.contains(ReplicatedBChain.toStr(removedNode))

  override def prune(removedNode: UniqueAddress, collapseInto: UniqueAddress): ReplicatedBChain = {
    val rm = ReplicatedBChain.toStr(removedNode)
    self.clock.versions.get(rm) match {
      case Some(v) ⇒
        val c            = ReplicatedBChain.toStr(collapseInto)
        val vv           = self.clock.versions.get(c).getOrElse(0L) + v
        val updatedClock = akka.cluster.VectorClock(self.clock.versions.removed(rm) + (c → vv))
        self.copy(clock = updatedClock)
      case None ⇒
        self
    }
  }

  override def pruningCleanup(removedNode: UniqueAddress): ReplicatedBChain = {
    val updatedClock = akka.cluster.VectorClock(self.clock.versions.removed(ReplicatedBChain.toStr(removedNode)))
    self.copy(clock = updatedClock)
  }
}
