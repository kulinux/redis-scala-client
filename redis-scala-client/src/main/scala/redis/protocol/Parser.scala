package redis.protocol

import java.io.InputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import scala.util.Try

import scala.language.implicitConversions

class RDProtocolException(msg: String, e: Exception) extends Exception(msg, e) {
  def this(msg: String) {
    this(msg, null)
  }
  def this(e: Exception) {
    this(null, e)
  }
}
trait RDReader {
  def read(): Int
  def readInt(): Int
  def readString(): String
  def read(length: Int): Array[Byte]
  def skip(length: Long): Long
}

class RDInputReader(is: InputStream) extends RDReader {
  def read(): Int = is.read()
  def readInt(): Int =
    try {
      readString().toInt
    } catch {
      case e: Exception => throw new RDProtocolException(e)
    }

  def skip(length: Long): Long = is.skip(length)
  def read(length: Int): Array[Byte] = {
    val avail = is.available()
    if (avail >= length) {
      val arr = new Array[Byte](length)
      val res = is.read(arr)
      if (res != length)
        throw new RDProtocolException(
          s"read eof while looking for $length bytes"
        )
      return arr
    } else {
      val bos = new ByteArrayOutputStream()
      var read = -1
      var count = 0
      read = is.read()
      while (read >= 0 && count < length) {
        count = count + 1
        bos.write(read)
        read = is.read()
        if (read == -1)
          throw new RDProtocolException(
            s"read eof while looking for $length bytes"
          )
      }
      return bos.toByteArray()
    }
  }

  def readString(): String = {
    val bos = new ByteArrayOutputStream()
    var encountR = false
    var read = is.read()
    if (read == -1)
      throw new RDProtocolException("ReadString error, eof reached")
    while (read >= 0) {
      if (read == -1)
        throw new RDProtocolException("ReadString error, \r\n not found")
      encountR = read == '\r'
      if (read == '\r') {
        is.mark(2)
        if (is.read() == '\n') {
          return new String(bos.toByteArray())
        } else {
          is.reset()
        }
      }
      bos.write(read)
      read = is.read()
    }
    throw new RDProtocolException("Error, \\r\\n not found")
  }

}

class Parser(reader: RDReader) {
  def parseSimpleString() = reader.readString()

  def parseErrors() = reader.readString()

  def parseInteger() = reader.readInt()

  def parseBulkString(): Array[Byte] = {
    val length = reader.readInt()
    val res = reader.read(length)
    reader.skip(2)
    res
  }

  def parseArray(): Seq[RDMessage] = {
    val length = reader.readInt()
    1 to length map (_ => parse())
  }

  def parse(): RDMessage = {
    var ch = reader.read()
    if (ch == -1) throw new RDProtocolException("No next message")
    ch.toChar match {
      case '+' => RDSimpleString(parseSimpleString())
      case '-' => RDError(parseErrors())
      case ':' => RDInteger(parseInteger())
      case '$' => RDBulkString(parseBulkString())
      case '*' => RDArray(parseArray())
      case unknown =>
        throw new RDProtocolException(s"Unknown start of msg $unknown")
    }
  }
}

trait Marshaller[F] {
  def marshall(m: F): Array[Byte] 
}

object Marshallers {
  implicit object RDSimpleMarshaller extends Marshaller[RDSimpleString] {
    def marshall(m: RDSimpleString) = ("+" + m.value + "\r\n").getBytes()
  }
  implicit object RDErrorMarshaller extends Marshaller[RDError] {
    def marshall(m: RDError) = ("-" + m.value + "\r\n").getBytes()
  }
  implicit object RDIntegerMarshaller extends Marshaller[RDInteger] {
    def marshall(m: RDInteger) = (":" + m.value + "\r\n").getBytes()
  }
  implicit object RDBulkStringMarshaller extends Marshaller[RDBulkString] {
    def marshall(m: RDBulkString) =
      ("$" + m.value.length + "\r\n").getBytes ++
        m.value ++ "\r\n".getBytes()
  }
  implicit object RDArrayMarshaller extends Marshaller[RDArray] {

    def marshall(m: RDArray) = {
      ("*" + m.value.size + "\r\n").getBytes ++
        m.value
            .map(msg => RDMessageMarshaller.marshall(msg)).foldLeft(Array[Byte]())(_ ++ _)
    }
  }

  implicit object RDMessageMarshaller extends Marshaller[RDMessage] {

    def marshall(m: RDMessage): Array[Byte] = m match {
      case msg: RDArray        => RDArrayMarshaller.marshall(msg)
      case msg: RDBulkString   => RDBulkStringMarshaller.marshall(msg)
      case msg: RDError        => RDErrorMarshaller.marshall(msg)
      case msg: RDInteger      => RDIntegerMarshaller.marshall(msg)
      case msg: RDSimpleString => RDSimpleMarshaller.marshall(msg)
    }

  }

  def marshall[T](msg: T)(implicit marshaller: Marshaller[T]): Array[Byte] =
    marshaller.marshall(msg)
}

object SyntaxMarshaller {
  import Marshallers._
  class SyntaxMarshaller(val msg: RDMessage) {
    def marshall(): Array[Byte] = Marshallers.marshall(msg)
  }
  implicit def toSyntaxMarshaller(s: RDMessage) = new SyntaxMarshaller(s)
}
