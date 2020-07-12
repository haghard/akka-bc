package bchain

import spray.json.JsObject

case class BlockChain(chain: List[Block]) {

  def latest: Block = chain.head

  def size: Int = chain.size

  def fromBlocks(chain: List[Block]): BlockChain = copy(chain)

  def nextEmptyBlock: Block =
    Block(latest.seqNum + 1L, latest.hash, JsObject(), System.currentTimeMillis)

  def nextBlock(data: JsObject): Block =
    Block(latest.seqNum + 1L, latest.hash, data, System.currentTimeMillis)

  def +(block: Block): (Boolean, BlockChain) =
    if (isValid(block)) (true, BlockChain(block :: chain)) else (false, this)

  private def isValid(newBlock: Block): Boolean =
    chain.head.seqNum == newBlock.seqNum - 1 && chain.head.hash == newBlock.prevHash

}
