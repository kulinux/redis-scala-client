package redis.protocol


sealed trait Message
case class RDSimpleString(value: String) extends Message
case class RDError(value: String) extends Message
case class RDInteger(value: Int) extends Message
case class RDBulkString(value: Seq[Byte], length: Int) extends Message
case class RDArray(value: Seq[Message]) extends Message
