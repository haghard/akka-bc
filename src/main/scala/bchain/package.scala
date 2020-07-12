import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ThreadLocalRandom

import spray.json.{DefaultJsonProtocol, JsObject, RootJsonFormat, _}

import scala.annotation.tailrec
import scala.math.BigDecimal.RoundingMode

package object bchain {

  final case class MinerNode(host: String, port: Int)

  object Implicits {
    implicit val nodeOrdering = new scala.Ordering[MinerNode] {
      override def compare(a: MinerNode, b: MinerNode) =
        Ordering
          .fromLessThan[MinerNode] { (x, y) ⇒
            if (x.host != y.host) x.host.compareTo(y.host) < 0
            else if (x.port != y.port) x.port < y.port
            else false
          }
          .compare(a, b)
    }
  }

  //https://github.com/TeamWanari/scala-coin

  object Difficulty {
    ////BigDecimal(BigDecimal.decimal(math.pow(2, 224)).toBigInt, 16)
    val tMax = BigDecimal(BigInt("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16))

    def checkHash(hash: String, difficulty: Double): Boolean = {
      //https://bitcoin.stackexchange.com/a/35807
      val t = (tMax / difficulty).toBigInt
      val h = BigInt(hash, 16)
      h < t
    }

    //https://bitcoin.stackexchange.com/a/35807
    def higherThen(hash: String, limit: BigInt): Boolean = {
      val h = BigInt(hash, 16)
      h > limit
    }

    def adjustDifficulty(currentDiff: Double, elapsedTime: Long, expectedTime: Long): Double =
      BigDecimal(expectedTime.toDouble / elapsedTime.toDouble * currentDiff)
        .setScale(2, RoundingMode.DOWN)
        .toDouble
  }

  object Crypto {
    private val hexDigits = "0123456789ABCDEF"

    def toHex(i: Int): String =
      new StringBuilder()
        .append(hexDigits(i >> 4))
        .append(hexDigits(i & 15))
        .toString()

    def bytesToHex(bytes: Array[Byte]): String = {
      val sb = new StringBuilder()
      for (b ← bytes)
        sb.append(String.format("%02X ", b: java.lang.Byte))
      sb.toString
    }

    //Hash String via SHA-256
    def sha256Hex(text: String): String = {
      //digest contains the hashed string and hex contains a hexadecimal ASCII string with left zero padding.
      val digest =
        new java.math.BigInteger(1, MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)))
      val hex = String.format("%064x", digest)
      hex
    }
  }

  case class Block(seqNum: Long, prevHash: String, data: JsObject, ts: Long, nonce: String = "1") {

    def hash: String = Crypto.sha256Hex(toString)

    s"""
      |$seqNum
      |$prevHash
      |$ts
      |$nonce
      |""".stripMargin

    override def toString: String =
      this.toJson.compactPrint
  }

  object Block extends DefaultJsonProtocol {
    implicit val formatter: RootJsonFormat[Block] =
      jsonFormat5(Block.apply)

    val genesis = Block(0L, "", JsObject(), 0L)

    /*
     * POW
     * The probability of hash to start with 6 leading zeros: 1/pow(2,6)
     */
    def mine(b: Block, leadingZeros: Int = 6): Block = {
      val start = System.currentTimeMillis()

      @tailrec def guess(b: Block, prefix: String): Block =
        if (isValid(b, prefix)) b
        else
          //if (ThreadLocalRandom.current().nextDouble() > 0.96) Thread.sleep(10)
          guess(b.copy(nonce = (BigInt(b.nonce, 16) + BigInteger.ONE).toString(16)), prefix)

      val expectedPrefix = "0" * leadingZeros
      val r              = guess(b, expectedPrefix)

      println("latency: " + (System.currentTimeMillis() - start))
      r
    }

    def isValid(b: Block, prefix: String): Boolean =
      b.hash.startsWith(prefix)
  }
}
