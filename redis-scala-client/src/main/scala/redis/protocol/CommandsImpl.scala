package redis.protocol


import java.net.InetSocketAddress
import java.io.{InputStream, ByteArrayInputStream}

import cats._
import cats.data._
import cats.implicits._
import cats.effect.{Blocker, Concurrent, ContextShift, Sync, ExitCode, IO, IOApp}

import fs2.{Chunk, Stream}
import fs2.io.tcp.{Socket, SocketGroup}

import Marshallers._
import SyntaxMarshaller._

class Redis[F[_]: Concurrent: ContextShift] {
  private val marshaller = new CommandMarshaller
  private val parser = new CommandParser
  private val sender = new CommandRedisSender

  def ping(req: PingRequest = PingRequest(Option.empty)): F[PingResponse] =
    sendCommand(req, marshaller.ping, parser.ping)

  def aclList(req: AclListRequest = new AclListRequest()): F[AclListResponse] =
    sendCommand(req, marshaller.aclList, parser.aclList)

  private def sendCommand[Req <: CommandRequest, Res <: CommandResponse](
      req: Req,
      marsh: Req => RDArray,
      pars: RDMessage => F[Res]
  ): F[Res] = {
    for {
      lowLevelReq <- marsh(req).pure[F]
      lowLevelRsp <- sender.send(lowLevelReq)
      msgRsp <- pars(lowLevelRsp)
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

  def aclList(req: AclListRequest): RDArray = 
      RDArray(RDBulkString("ACL"), RDBulkString("LIST"))
}

class CommandParser[F[_]: Concurrent: ContextShift](implicit M: MonadError[F, Throwable])  {
  def ping(rsp: RDMessage): F[PingResponse] = {
    if(rsp.isInstanceOf[RDError]) {
      return M.raiseError(new RuntimeException(rsp.asInstanceOf[RDError].value))
    }
    assert(rsp.isInstanceOf[RDBulkString])
    PingResponse(new String(rsp.asInstanceOf[RDBulkString].value)).pure[F]
  }

  def aclList(rsp: RDMessage): F[AclListResponse] = {
    if(rsp.isInstanceOf[RDError]) {
      return M.raiseError(new RuntimeException(rsp.asInstanceOf[RDError].value))
    }
    assert(rsp.isInstanceOf[RDArray])
    AclListResponse(
        rsp.asInstanceOf[RDArray].value
            .map(_.asInstanceOf[RDBulkString])
            .map(_.value.map(_.toChar).mkString)
    ).pure[F]
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
    /*
    redis.ping(new PingRequest(Some("Hola,holita")))
        .map(rsp => println("Respuesta " + rsp.msg))
        .map(rsp => ExitCode.Success)
        */

    redis.aclList( new AclListRequest() )
        .map(rsp => println("Respuesta " + rsp.msg))
        .map(rsp => ExitCode.Success)
        .handleError( err => {
          println("An error happened")
          err.printStackTrace()
          ExitCode.Error
        })
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
