package redis.protocol


sealed trait RDMessage

case class RDSimpleString(value: String) extends RDMessage

case class RDError(value: String) extends RDMessage

case class RDInteger(value: Int) extends RDMessage

sealed trait RDBulkString extends RDMessage
case object RDNullBulkString extends RDBulkString
case class RDSomeBulkString(value: Array[Byte]) extends RDBulkString 
object RDBulkString {
    def apply(str: String) = new RDSomeBulkString(str.getBytes())
}

case class RDArray(value: RDMessage*) extends RDMessage
