package zio.entity.runtime.k8dns

import zio.clock.Clock
import zio.duration.durationInt
import zio.entity.annotations.MethodId
import zio.entity.core.Combinators.combinators
import zio.entity.core.Entity.entity
import zio.entity.core.Fold.impossible
import zio.entity.core._
import zio.entity.data.Tagging.Const
import zio.entity.data.{ConsumerId, EntityProtocol, EventTag, Tagging}
import zio.entity.macros.RpcMacro
import zio.entity.readside.ReadSideParams
import zio.memberlist.Memberlist.SwimEnv
import zio.test.Assertion.equalTo
import zio.test.TestAspect.sequential
import zio.test.environment.TestEnvironment
import zio.test.{assert, DefaultRunnableSpec, ZSpec}
import zio.{Has, IO, UIO, ZEnv, ZLayer}

class RuntimeSpec extends DefaultRunnableSpec {
  import CounterEntity._
  private val stores: ZLayer[Any, Nothing, Has[Stores[String, CountEvent, Int]]] = Clock.live to MemoryStores.live[String, CountEvent, Int](100.millis, 2)

  private val swimEnv: ZLayer[Any, Nothing, SwimEnv] = ???
  private val runtimeServer: ZLayer[SwimEnv, Throwable, Has[RuntimeServer]] =
    SwimRuntimeServer.live(5.seconds, 5.seconds, 3.seconds)

  private val layer: ZLayer[ZEnv, Throwable, Has[Entity[String, CounterCommandHandler, Int, CountEvent, String]]] =
    Clock.any and stores and (swimEnv to runtimeServer) to Runtime
      .entityLive("Counter", CounterEntity.tagging, EventSourcedBehaviour(new CounterCommandHandler, CounterEntity.eventHandlerLogic, _.getMessage))
      .toLayer

  override def spec: ZSpec[TestEnvironment, Any] = suite("A zio native runtime")(
    testM("receives commands and updates state") {
      (for {
        counter <- entity[String, CounterCommandHandler, Int, CountEvent, String]
        res <- counter("key")(
          _.increase(3)
        )
        finalRes <- counter("key")(
          _.decrease(2)
        )
        secondEntityRes <- counter("secondKey") {
          _.increase(1)
        }
        secondEntityFinalRes <- counter("secondKey") {
          _.increase(5)
        }
        fromState <- counter("key")(
          _.getValue
        )
      } yield {
        assert(res)(equalTo(3)) &&
        assert(finalRes)(equalTo(1)) &&
        assert(secondEntityRes)(equalTo(1)) &&
        assert(secondEntityFinalRes)(equalTo(6)) &&
        assert(fromState)(equalTo(1))
      }).provideSomeLayer[TestEnvironment](layer)
    },
    testM("Read side processing processes work") {
      (for {
        counter <- entity[String, CounterCommandHandler, Int, CountEvent, String]
        promise <- zio.Promise.make[Nothing, Int]
        killSwitch <- counter
          .readSideSubscription(ReadSideParams("read", ConsumerId("1"), CounterEntity.tagging, 2, ReadSide.countIncreaseEvents(promise, _, _)), _.getMessage)
        _      <- counter("key")(_.increase(2))
        _      <- counter("key")(_.decrease(1))
        result <- promise.await
      } yield (assert(result)(equalTo(2)))).provideSomeLayer[TestEnvironment](Clock.live and layer)
    }
  ) @@ sequential
}

sealed trait CountEvent
case class CountIncremented(number: Int) extends CountEvent
case class CountDecremented(number: Int) extends CountEvent

class CounterCommandHandler {
  type EIO[Result] = Combinators.EIO[Int, CountEvent, String, Result]

  @MethodId(1)
  def increase(number: Int): EIO[Int] = combinators { c =>
    c.read flatMap { res =>
      c.append(CountIncremented(number)).as(res + number)
    }
  }

  @MethodId(2)
  def decrease(number: Int): EIO[Int] = combinators { c =>
    c.read flatMap { res =>
      c.append(CountDecremented(number)).as(res - number)
    }
  }

  @MethodId(3)
  def noop: EIO[Unit] = combinators(_.ignore)

  @MethodId(4)
  def getValue: EIO[Int] = combinators(_.read)
}

object CounterEntity {

  val tagging: Const[String] = Tagging.const[String](EventTag("Counter"))

  val eventHandlerLogic: Fold[Int, CountEvent] = Fold(
    initial = 0,
    reduce = {
      case (state, CountIncremented(number)) => UIO.succeed(state + number)
      case (state, CountDecremented(number)) => UIO.succeed(state - number)
      case _                                 => impossible
    }
  )

  implicit val counterProtocol: EntityProtocol[CounterCommandHandler, Int, CountEvent, String] =
    RpcMacro.derive[CounterCommandHandler, Int, CountEvent, String]

}

object ReadSide {

  def countIncreaseEvents(promise: zio.Promise[Nothing, Int], id: String, countEvent: CountEvent): IO[String, Unit] =
    countEvent match {
      case CountIncremented(value) =>
        promise.succeed(value).unit
      case _ => UIO.unit
    }

}
