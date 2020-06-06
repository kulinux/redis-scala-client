# Scala Client

Implementation of Redis Client

## Usage

```scala

  private implicit val cs = IO.contextShift(global)

  val redis = new Redis[IO]

  val program = for {
      _ <- redis.set("monk", "monkv")
      value <- redis.get("monk")
    } yield value

   val rsp = program.unsafeRunSync()
```

## Commands implemented

* PING
* SET
* GET
* MSET
* MGET

## Scala client protocol REDIS

Documentation about protocol implemented

* [Specification](https://redis.io/topics/protocol)
* [Commands](https://redis.io/commands)
