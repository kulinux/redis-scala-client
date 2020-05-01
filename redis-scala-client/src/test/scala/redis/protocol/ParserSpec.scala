package redis.protocol

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.ByteArrayInputStream

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
        val msg = "$6\r\nfoobar\r\n"
        new String(parseMsg(msg).asInstanceOf[RDBulkString].value) should be ("foobar")
    }

}