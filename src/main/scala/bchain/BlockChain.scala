package bchain

import bchain.BlockChain.DifficultyConf
import spray.json.JsObject

import scala.concurrent.duration.FiniteDuration

object BlockChain {

  case class DifficultyConf(blockPeriod: Int, expectedTime: Long)

  object DifficultyConf {
    def apply(blockPeriod: Int, expectedTime: FiniteDuration): DifficultyConf =
      new DifficultyConf(blockPeriod, expectedTime.toMillis)
  }
}

case class BlockChain(chain: List[Block])(implicit cfg: DifficultyConf) {

  def latest: Block = chain.head

  def size: Int = chain.size

  def fromBlocks(chain: List[Block]): BlockChain = copy(chain)

  def nextEmptyBlock: Block =
    Block(latest.seqNum + 1L, latest.hash, JsObject(), calcNextDiff, System.currentTimeMillis)

  def nextBlock(data: JsObject): Block =
    Block(latest.seqNum + 1L, latest.hash, data, calcNextDiff, System.currentTimeMillis)

  def +(block: Block): (Boolean, BlockChain) =
    if (isValid(block)) (true, BlockChain(block :: chain)) else (false, this)

  private def isValid(newBlock: Block) =
    chain.head.seqNum == newBlock.seqNum - 1 &&
    chain.head.hash == newBlock.prevHash &&
    calcNextDiff == newBlock.difficulty

  private def calcNextDiff: Double =
    if ((chain.size >= cfg.blockPeriod) && (chain.size % cfg.blockPeriod == 1)) {
      val sample         = chain.take(cfg.blockPeriod)
      val timeElapsed    = sample.head.ts - sample.last.ts
      val expectedTime   = cfg.expectedTime * cfg.blockPeriod
      val nextDifficulty = Difficulty.adjustDifficulty(latest.difficulty, timeElapsed, expectedTime)
      nextDifficulty
    } else latest.difficulty
}
