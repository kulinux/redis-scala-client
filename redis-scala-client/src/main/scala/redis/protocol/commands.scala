package redis.protocol

trait CommandRequest
trait CommandResponse

case class PingRequest(msg: Option[String]) extends CommandRequest
case class PingResponse(msg: String) extends CommandResponse

case class AclListRequest() extends CommandRequest
case class AclListResponse(msg: Seq[String]) extends CommandResponse
