package redis.protocol

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.ByteArrayInputStream

import Marshallers._
import SyntaxMarshaller._

class ParserSpec extends AnyFlatSpec with Matchers {

    def parseMsg(msg: String) = {
        val is = new ByteArrayInputStream(msg.getBytes())
        val parse: Parser = new Parser(new RDInputReader(is))

        parse.parse()
    }

    "Parser" should "parse simple string" in {
        val msg = "+OK\r\n"
        parseMsg(msg) should be (RDSimpleString("OK"))
    }

    "Parser" should "parse int" in {
        val msg = ":1000\r\n"
        parseMsg(msg) should be (RDInteger(1000))
    }

    "Parser" should "parse bulk string" in {
        val msg = "*5\r\n:1\r\n:2\r\n:3\r\n$6\r\nfoobar\r\n+OK\r\n"
        val res = parseMsg(msg).asInstanceOf[RDArray].value
        res.length shouldBe(5)
    }

    "Parser" should "parse array" in {
        val msg = "$6\r\nfoobar\r\n"
        new String(parseMsg(msg).asInstanceOf[RDBulkString].value) should be ("foobar")
    }

    "Parser" should "error on parse wrong simple string" in {
        val msg = "+OK\r"
        assertThrows[RDProtocolException] {
            parseMsg(msg)
        }
    }

    "Parser" should "error on parse wrong int" in {
        val msg = ":10a00\r\n"
        assertThrows[RDProtocolException] {
            parseMsg(msg)
        }
    }

     "Parser" should "error on parse wrong bulk string" in {
        val msg = "$6\rfoobar\r\n"
        assertThrows[RDProtocolException] {
            parseMsg(msg)
        }
    }

    "Marshall" should "work" in {
        val msgStr = new RDSimpleString("PING")
        msgStr.marshall() should be("+PING\r\n".getBytes())
        val msgInt = RDInteger(1000)
        msgInt.marshall should be(":1000\r\n".getBytes())

        val msgBulk = RDBulkString("foobar".getBytes())
        msgBulk.marshall should be("$6\r\nfoobar\r\n".getBytes())


        val msgArr = RDArray(Seq(
            RDBulkString("foo".getBytes()),
            RDBulkString("bar".getBytes())
        ))
        msgArr.marshall should be("*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n".getBytes())

    }



}