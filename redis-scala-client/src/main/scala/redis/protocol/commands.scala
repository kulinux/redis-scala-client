package redis.protocol

import Marshallers._
import SyntaxMarshaller._
import cats.effect.IO
import java.io.InputStream

case class RDRequest(req: Seq[String])

trait CommandRequest
trait CommandResponse

case class PingRequest(msg: Option[String]) extends CommandRequest
case class PingResponse(msg: String) extends CommandResponse


class Redis {
    val marshaller = new CommandMarshaller
    val parser = new CommandParser
    val sender = new CommandRedisSender
    def ping(req: PingRequest): IO[PingResponse] =
        sendCommand(req, marshaller.ping, parser.ping)

    def sendCommand[Req <: CommandRequest, Res <: CommandResponse](
        req: Req,
        marsh: Req => RDArray,
        pars: RDMessage => Res): IO[Res] = {
        for {
            lowLevelReq <- IO.pure(marsh(req))
            lowLevelRsp <- sender.send(lowLevelReq)
            msgRsp <- IO.pure(pars(lowLevelRsp))
        } yield msgRsp
    }
}


class CommandMarshaller {
    def ping(req: PingRequest): RDArray = {
        if(req.msg.isEmpty)
            RDArray(RDBulkString("PING"))
        else
            RDArray(RDBulkString("PING"), RDBulkString(req.msg.get))
    }
}

class CommandParser {
    def ping(req: RDMessage): PingResponse = {
        assert(req.isInstanceOf[RDSimpleString])
        PingResponse(req.asInstanceOf[RDSimpleString].value)
    }
}

class CommandRedisSender {
    def sendToSocket(byts: Array[Byte]): IO[Parser] = ???
    def send(msg: RDArray): IO[RDMessage] = {
        for {
            reqBytes <- IO.pure(msg.marshall())
            parser <- sendToSocket(reqBytes)
            rspMsg <- IO.pure(parser.parse())
        } yield rspMsg
    }
}



//request -> a resp array of bulk string
//response -> depende del comando
//class RDRequest = RESP Array of BulkString