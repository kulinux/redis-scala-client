package redis.protocol

import Marshallers._
import SyntaxMarshaller._
import cats.effect.IO
import java.io.InputStream

import fs2.{Chunk, Stream}
import fs2.io.tcp.{Socket, SocketGroup}
import cats.effect.{Blocker, Concurrent, ContextShift, Sync}
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import java.net.InetSocketAddress
import java.io.ByteArrayInputStream
import cats.effect.Resource.Par

class Redis[F[_]: Concurrent: ContextShift] {
  private val marshaller = new CommandMarshaller
  private val parser = new CommandParser
  private val sender = new CommandRedisSender

  def ping(req: PingRequest): F[PingResponse] =
    sendCommand(req, marshaller.ping, parser.ping)

  private def sendCommand[Req <: CommandRequest, Res <: CommandResponse](
      req: Req,
      marsh: Req => RDArray,
      pars: RDMessage => Res
  ): F[Res] = {
    for {
      lowLevelReq <- marsh(req).pure[F]
      lowLevelRsp <- sender.send(lowLevelReq)
      msgRsp <- pars(lowLevelRsp).pure[F]
    } yield msgRsp
  }
}

class CommandMarshaller {
  def ping(req: PingRequest): RDArray = {
    if (req.msg.isEmpty)
      RDArray(RDBulkString("PING"))
    else
      RDArray(RDBulkString("PING"), RDBulkString(req.msg.get))
  }
}

class CommandParser {
  def ping(req: RDMessage): PingResponse = {
    assert(req.isInstanceOf[RDBulkString])
    PingResponse(new String(req.asInstanceOf[RDBulkString].value))
  }
}

class CommandRedisSender {
  val host = "127.0.0.1"
  val port = 6379

  def send[F[_]: Concurrent: ContextShift](msg: RDArray): F[RDMessage] = {
    for {
      reqBytes <- msg.marshall().pure[F]
      parser <- sendToSocket(reqBytes)
      rspMsg <- parser.parse().pure[F]
    } yield rspMsg
  }

  private def sendToSocket[F[_]: Concurrent: ContextShift](
      toSend: Array[Byte]
  ): F[Parser] =
    Sck
      .oneShotClient(host, port, toSend)
      .map(new ByteArrayInputStream(_))
      .map(new RDInputReader(_))
      .map(new Parser(_))

}

object Sck {

  def oneShotClient[F[_]: Concurrent: ContextShift](
      host: String,
      port: Int,
      toSend: Array[Byte]
  ): F[Array[Byte]] =
    Blocker[F].use { blocker =>
      SocketGroup[F](blocker).use { socketGroup =>
        Sck.client[F](socketGroup, host, port, toSend)
      }
    }

  def client[F[_]: Concurrent: ContextShift](
      socketGroup: SocketGroup,
      host: String,
      port: Int,
      toSend: Array[Byte]
  ): F[Array[Byte]] =
    socketGroup.client(new InetSocketAddress(host, port)).use { socket =>
      socket.write(Chunk.bytes(toSend)) >>
        socket.read(8192).map { response => response.get.toArray }
    }
}

object RedisClientApp extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {

    val redis = new Redis[IO]
    redis.ping(new PingRequest(Some("Hola,holita")))
        .map(rsp => println("Respuesta " + rsp.msg))
        .map(rsp => ExitCode.Success)
  }
}

/*
object ClientApp extends IOApp
{

  def run(args: List[String]): IO[ExitCode] =
    Blocker[IO].use { blocker =>
      SocketGroup[IO](blocker).use { socketGroup =>
        Sck.client[IO](socketGroup, "127.0.0.1", 5555, "Hello socket".getBytes())
            .map(rsp => println("Respuesta " + new String(rsp)))
      }
    }.as(ExitCode.Success)
}
 */
