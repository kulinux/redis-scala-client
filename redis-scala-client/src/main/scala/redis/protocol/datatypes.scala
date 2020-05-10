package redis.protocol


sealed trait RDMessage

case class RDSimpleString(value: String) extends RDMessage

case class RDError(value: String) extends RDMessage

case class RDInteger(value: Int) extends RDMessage

case class RDBulkString(value: Array[Byte]) extends RDMessage
object RDBulkString {
    def apply(str: String) = new RDBulkString(str.getBytes())
}

case class RDArray(value: RDMessage*) extends RDMessage
