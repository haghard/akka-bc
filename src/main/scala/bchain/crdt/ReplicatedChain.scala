package bchain
package crdt

//import akka.cluster.UniqueAddress
//import scala.collection.immutable.TreeMap

import akka.actor.Address
import akka.cluster.Member

import scala.annotation.nowarn
import akka.cluster.ddata.ReplicatedData

/*
https://www.geeksforgeeks.org/merge-two-sorted-arrays/
https://github.com/haghard/dr-chatter/blob/f60e3174f6f58afc824fb5c47109f7a1bdf0daff/src/main/scala/chatter/crdt/ChatTimeline.scala#L30
https://github.com/typelevel/algebra/blob/46722cd4aa4b01533bdd01f621c0f697a3b11040/docs/docs/main/tut/typeclasses/overview.md
 */
final case class ReplicatedChain(
  blockChain: BlockChain,
  versions: VersionVectors[Address] = VersionVectors.empty[Address](Member.addressOrdering)
) extends ReplicatedData { self ⇒

  override type T = ReplicatedChain

  def +(block: Block, node: Address): ReplicatedChain = {
    val (added, updatedChain) = self.blockChain + block
    if (added) copy(updatedChain, self.versions.+:(node)) else this
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
  override def merge(that: ReplicatedChain): ReplicatedChain =
    if (self.versions < that.versions)
      that
    else if (self.versions > that.versions)
      this
    else if (self.versions <> that.versions) {
      //val blocks = merge2(blockChain.chain, that.blockChain.chain)
      //ReplicatedChain(blockChain.fromBlocks(blocks), versions merge that.versions)

      //The longest chain wins
      val winner =
        if (self.blockChain.size == that.blockChain.size)
          //(earliest write wins) The youngest chain wins
          if (self.blockChain.latest.ts < that.blockChain.latest.ts) this else that
        else if (self.blockChain.size > that.blockChain.size) this
        else that

      ReplicatedChain(winner.blockChain, versions merge that.versions)
    } else this //  ==
}
