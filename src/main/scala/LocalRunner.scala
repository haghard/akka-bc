import bchain.{Block, BlockChain}
import bchain.BlockChain.DifficultyConf
import Helpers._
import scala.concurrent.duration._

object LocalRunner extends App {
  implicit val config = DifficultyConf(5, 1.minutes)

  val blockChain = BlockChain(Block.genesis :: Nil)

  val one = Block.mine(blockChain.nextBlock(transaction))
  println(s"1 $one")
  val (r1, bc1) = blockChain + one

  val two = Block.mine(bc1.nextBlock(transaction))
  println(s"2. $two")
  val (r2, bc2) = bc1 + two

  val three = Block.mine(bc2.nextBlock(transaction))
  println(s"3. $three")
  val (r3, bc3) = bc2 + three

  val four = Block.mine(bc3.nextBlock(transaction))
  println(s"4. $four")
  val (r4, bc4) = bc3 + four

  val five = Block.mine(bc4.nextBlock(transaction))
  println(s"5. $five")
  val (r5, bc5) = bc4 + five

  val six = Block.mine(bc5.nextBlock(transaction))
  println(s"6. $six")
  val (r6, bc6) = bc5 + six

  val seven = Block.mine(bc6.nextBlock(transaction))
  println(s"7. $seven")
  val (r7, bc7) = bc6 + seven

  val eight = Block.mine(bc7.nextBlock(transaction))
  println(s"8. $eight")
  val (r8, bc8) = bc7 + eight

  val nine = Block.mine(bc8.nextBlock(transaction))
  println(s"9. $nine")
  val (r9, bc9) = bc8 + nine

  val ten = Block.mine(bc9.nextBlock(transaction))
  println(s"10. $ten")
  val (r10, bc10) = bc9 + ten

  val eleven = Block.mine(bc10.nextBlock(transaction))
  println(s"11. $eleven")
  val (r11, bc11) = bc10 + eleven
}
