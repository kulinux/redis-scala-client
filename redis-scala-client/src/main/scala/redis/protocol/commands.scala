package redis.protocol

trait CommandRequest
trait CommandResponse

case class PingRequest(msg: Option[String]) extends CommandRequest
case class PingResponse(msg: String) extends CommandResponse


case class SetRequest(
    key: String,
    value: String,
    exPx: Option[Either[Long, Long]] = Option.empty,
    nxNotXX: Option[Boolean] = Option.empty,
    keepTtl: Option[Boolean] = Option.empty
) extends CommandRequest
case class SetResponse(msg: String) extends CommandResponse
