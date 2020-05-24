package redis.protocol

import java.net.InetSocketAddress
import java.io.{InputStream, ByteArrayInputStream}

import cats._
import cats.data._
import cats.implicits._
import cats.effect.{
  Blocker,
  Concurrent,
  ContextShift,
  Sync,
  ExitCode,
  IO,
  IOApp
}

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

  def set(req: SetRequest): F[SetResponse] =
    sendCommand(req, marshaller.set, parser.set)

  def set(key: String, value: String): F[Unit] =
    set(SetRequest(key, value)).map(_ => ())

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

  def set(req: SetRequest): RDArray = {
    def toSet[T](key: String, value: Option[T]): Seq[String] =
      value.map(xx => Seq(key, xx.toString)).getOrElse(Seq())

    def toSetBoolTrue(key: String, value: Option[Boolean]): Seq[String] =
      value.filter(v => v).map(xx => Seq(key)).getOrElse(Seq())

    def toSetBool(key1: String, key2: String, value: Option[Boolean]): Seq[String] =
      value.map(xx => xx match {
        case true => Seq(key1)
        case false => Seq(key2)
      }).getOrElse(Seq())

    def toSetOptionEither[A, B](
        key1: String,
        key2: String,
        value: Option[Either[A, B]]
    ): Seq[String] = {
      if(value.isEmpty) return Seq()
      value.get match {
        case Left(value) => Seq(key1, value)
        case Right(value) => Seq(key2, value)
      }
      return Seq()
    }

    val arr: Seq[String] =
      Seq("SET", req.key, req.value) ++
      toSetOptionEither("EX", "PX", req.exPx) ++
      toSetBool("NX", "XX", req.nxNotXX) ++
      toSetBoolTrue("KEEPTTL", req.keepTtl)

    println(arr)

    RDArray(arr.map(RDBulkString(_)): _*)
  }

}

class CommandParser[F[_]: Concurrent: ContextShift](
    implicit M: MonadError[F, Throwable]
) {
  def ping(rsp: RDMessage): F[PingResponse] = {
    if (rsp.isInstanceOf[RDError]) {
      return M.raiseError(new RuntimeException(rsp.asInstanceOf[RDError].value))
    }
    assert(rsp.isInstanceOf[RDBulkString])
    PingResponse(new String(rsp.asInstanceOf[RDBulkString].value)).pure[F]
  }

  def set(rsp: RDMessage): F[SetResponse] = {
    if (rsp.isInstanceOf[RDError]) {
      return M.raiseError(new RuntimeException(rsp.asInstanceOf[RDError].value))
    }
    assert(rsp.isInstanceOf[RDSimpleString])
    SetResponse(new String(rsp.asInstanceOf[RDSimpleString].value)).pure[F]
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
