package bchain.crdt

import scala.annotation.tailrec
import scala.collection.immutable.SortedMap

/*
 * The idea's taken from https://github.com/mboogerd/ccrdt/blob/c636848044283cd5ad193b63fb5be9f56f84ed7a/src/main/scala/com/github/mboogerd/ccrdt/crdt/VersionVector.scala#L26
 *
 * We say
 *  A dominates B (A > B) when compare
 *    [akka://us@127.0.0.1:2551 -> 1, akka://us@127.0.0.1:2552 -> 2] and [akka://us@127.0.0.1:2551 -> 1, akka://us@127.0.0.1:2552 -> 1]
 *    or
 *    [akka://us@127.0.0.1:2551 -> 1, akka://us@127.0.0.1:2552 -> 1, akka://us@127.0.0.1:2553 -> 1] and  [akka://us@127.0.0.1:2551 -> 1, akka://us@127.0.0.1:2552 -> 1]
 *
 *  B dominates A (B > A) when compare
 *    [akka://us@127.0.0.1:2551 -> 1, akka://us@127.0.0.1:2552 -> 2] and [akka://us@127.0.0.1:2551 -> 1, akka://us@127.0.0.1:2552 -> 3]
 *
 *
 *  A concurrent B (A <> B) when compare
 *
 *    [akka://us@127.0.0.1:2551 -> 13, akka://us@127.0.0.1:2552 -> 8]  <>  [akka://us@127.0.0.1:2551 -> 12, akka://us@127.0.0.1:2552 -> 9]
 *    which means both replicas have accepted one update on it's once without replication(synchronization)
 *    Merge result - [akka://us@127.0.0.1:2551 -> 13, akka://us@127.0.0.1:2552 -> 9]
 *
 */
object VersionVectors {

  sealed trait Ordering

  case object After      extends Ordering
  case object Before     extends Ordering
  case object Same       extends Ordering
  case object Concurrent extends Ordering

  // Marker to ensure that we do a full order comparison instead of bailing out early.
  private case object FullOrder extends Ordering

  def empty[A](implicit ord: scala.Ordering[A]) =
    VersionVectors[A](SortedMap.empty[A, Long](ord))
}

trait VersionVectorLike[T] {
  type VV <: VersionVectorLike[T]

  /** Increment the version for the node passed as argument. Returns a new VersionVector.
    */
  def +:(node: T): VV = increment(node)

  /** Increment the version for the node passed as argument. Returns a new VersionVector.
    */
  protected def increment(node: T): VV

  /** Returns the local view on the logical clock of the given node.
    */
  def version(node: T): Long

  /** Returns true if <code>this</code> and <code>that</code> are concurrent else false.
    */
  def <>(that: VV): Boolean

  /** Returns true if <code>this</code> is before <code>that</code> else false.
    */
  def <(that: VV): Boolean

  /** Returns true if <code>this</code> is after <code>that</code> else false.
    */
  def >(that: VV): Boolean

  /** Returns true if this VersionVector has the same history as the 'that' VersionVector else false.
    */
  def ==(that: VV): Boolean

  /** Computes the union of the nodes and maintains the highest clock value found for each
    */
  def merge(that: VV): VV

  /** Returns the number of nodes registered in this version vector
    */
  protected def size: Int
}

/** The original idea was taken from https://github.com/mboogerd/ccrdt
  */
final case class VersionVectors[T: scala.Ordering](entries: SortedMap[T, Long]) extends VersionVectorLike[T] {

  import VersionVectors._

  override type VV = VersionVectors[T]

  /** Increment the version for the node passed as argument. Returns a new VersionVector.
    */
  override protected def increment(node: T): VersionVectors[T] =
    VersionVectors(entries.updated(node, nodeClock(node) + 1L))

  /** Returns the local view on the logical clock of the given node.
    */
  override def version(node: T): Long = nodeClock(node)

  /** Returns true if <code>this</code> and <code>that</code> are concurrent else false.
    */
  def <>(that: VersionVectors[T]): Boolean = compareOnlyTo(that, Concurrent) eq Concurrent

  /** Returns true if <code>this</code> is before <code>that</code> else false.
    */
  def <(that: VersionVectors[T]): Boolean = compareOnlyTo(that, Before) eq Before

  /** Returns true if <code>this</code> is after <code>that</code> else false.
    */
  def >(that: VersionVectors[T]): Boolean = compareOnlyTo(that, After) eq After

  /** Returns true if this VersionVector has the same history as the 'that' VersionVector else false.
    */
  def ==(that: VersionVectors[T]): Boolean = compareOnlyTo(that, Same) eq Same

  /** Version vector comparison according to the semantics described by compareTo, with the ability to bail
    * out early if the we can't reach the Ordering that we are looking for.
    *
    * The ordering always starts with Same and can then go to Same, Before or After
    * If we're on After we can only go to After or Concurrent
    * If we're on Before we can only go to Before or Concurrent
    * If we go to Concurrent we exit the loop immediately
    *
    * If you send in the ordering FullOrder, you will get a full comparison.
    */
  private final def compareOnlyTo(that: VersionVectors[T], order: Ordering): Ordering = {
    val requestedOrder = if (order eq Concurrent) FullOrder else order

    @tailrec
    def compare(i1: Seq[(T, Long)], i2: Seq[(T, Long)], currentOrder: Ordering): Ordering = {
      if ((requestedOrder ne FullOrder) && (currentOrder ne Same) && (currentOrder ne requestedOrder)) currentOrder

      (i1, i2) match {
        case (h1 +: t1, h2 +: t2) ⇒
          // compare the nodes
          val nc = entries.ordering.compare(h1._1, h2._1)
          if (nc == 0)
            // both nodes exist compare the timestamps
            // same timestamp so just continue with the next nodes
            if (h1._2 == h2._2) compare(t1, t2, currentOrder)
            else if (h1._2 < h2._2)
              // t1 is less than t2, so i1 can only be Before
              if (currentOrder eq After) Concurrent
              else compare(t1, t2, Before)
            else
            // t2 is less than t1, so i1 can only be After
            if (currentOrder eq Before) Concurrent
            else compare(t1, t2, After)
          else if (nc < 0)
            // this node only exists in i1 so i1 can only be After
            if (currentOrder eq Before) Concurrent
            else compare(t1, h2 +: t2, After)
          else
          // this node only exists in i2 so i1 can only be Before
          if (currentOrder eq After) Concurrent
          else compare(h1 +: t1, t2, Before)

        case (h1 +: t1, _) ⇒
          // i2 is empty but i1 is not, so i1 can only be After
          if (currentOrder eq Before) Concurrent else After

        case (_, h2 +: t2) ⇒
          // i1 is empty but i2 is not, so i1 can only be Before
          if (currentOrder eq After) Concurrent else Before

        case _ ⇒
          currentOrder
      }
    }

    if (this eq that) Same
    else compare(entries.view.toSeq, that.entries.view.toSeq, Same)
  }

  override def merge(that: VersionVectors[T]): VersionVectors[T] = {

    def go(s1: SortedMap[T, Long], s2: SortedMap[T, Long], acc: SortedMap[T, Long]): SortedMap[T, Long] =
      if (s1.nonEmpty && s2.nonEmpty) {
        val (t1, c1) = s1.head
        val (t2, c2) = s2.head
        val order    = entries.ordering.compare(t1, t2)
        if (order == 0) {
          // This elements exists only in both maps, take the maximum logical clock value
          val newAcc = acc.updated(t1, math.max(c1, c2))
          go(s1.tail, s2.tail, newAcc)
        } else if (order < 0) {
          // This element exists only in c1
          val newAcc = acc.updated(t1, c1)
          go(s1.tail, s2, newAcc)
        } else {
          // This element exists only in c2
          val newAcc = acc.updated(t2, c2)
          go(s1, s2.tail, newAcc)
        }
      } else if (s1.isEmpty) acc ++ s2
      else acc ++ s1

    val newEntries = go(entries, that.entries, SortedMap.empty[T, Long])
    VersionVectors(newEntries)
  }

  override protected def size: Int = entries.size

  override def toString: String =
    entries.map { case (key, v) ⇒ key.toString + " -> " + v }.mkString("[", ", ", "]")

  private def nodeClock(node: T): Long = entries.getOrElse(node, 0)
}
