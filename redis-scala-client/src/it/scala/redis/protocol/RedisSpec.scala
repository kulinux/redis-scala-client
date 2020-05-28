package redis.protocol

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import cats._
import cats.data._
import cats.implicits._
import cats.effect._

import scala.concurrent.ExecutionContext.Implicits.global

class RedisSpec extends AnyFlatSpec with Matchers {

  private implicit val cs = IO.contextShift(global)

  val redis = new Redis[IO]

  "Ping" should "work" in {
    val program = redis
      .ping(new PingRequest(Some("Hola,holita")))
    val rsp = program.unsafeRunSync()
    rsp.msg shouldBe ("Hola,holita")
  }

  "Set" should "work" in {
    val program = redis
      .set("key", "value")

    val rsp = program.unsafeRunSync()
    rsp shouldBe (())
  }

  "Set" should "work with param" in {
    val program = redis
      .set(
        SetRequest(
          "key",
          "value4",
          10L.asLeft.some,
          false.some,
          true.some
        )
      )
    val rsp = program.unsafeRunSync()
    rsp.msg shouldBe ("OK")
  }

  "Get" should "work" in {
    val program = for {
      _ <- redis.set("monk", "monkv")
      value <- redis.get("monk")
    } yield value

    val rsp = program.unsafeRunSync()
    rsp shouldBe ("monkv".some)
  }

  "MSet" should "work" in {
    val program = redis
      .mset(Map("key55" -> "value", "key77" -> "value88"))

    val rsp = program.unsafeRunSync()
    rsp shouldBe (())
  }

  

}
