package redis.protocol

trait CommandRequest
trait CommandResponse

case class PingRequest(msg: Option[String]) extends CommandRequest
case class PingResponse(msg: String) extends CommandResponse
