package zio.entity.postgres.journal

import zio.clock.Clock
import zio.duration.durationInt
import zio.entity.core.journal.JournalEntry
import zio.entity.data.{EntityEvent, EventTag, Tagging}
import zio.entity.postgres.example.{AnEvent, AnEventMessage, FirstEventHappened, Key}
import zio.entity.postgres.journal.PostgresqlEventJournal.EventJournalStore
import zio.entity.postgres.snapshot.PostgresqlTestContainerManaged
import zio.test.Assertion.equalTo
import zio.test.environment.{TestClock, TestEnvironment}
import zio.test.{DefaultRunnableSpec, ZSpec, assert}
import zio.{Chunk, Has, NonEmptyChunk, ZEnv, ZIO, ZLayer}
import zio.entity.serializer.protobuf.ProtobufCodecs._
import zio.test.TestAspect.sequential

object PostgresqlEventJournalSpec extends DefaultRunnableSpec {

  private implicit val eventCodec = codecSealed[AnEvent, AnEventMessage]

  private val layer: ZLayer[ZEnv, Throwable, Has[EventJournalStore[Key, AnEvent]]] =
    (Clock.any and PostgresqlTestContainerManaged.transact) to PostgresqlEventJournal.make[Key, AnEvent]("testevent", 100.millis)

  private val tagging = Tagging.const[Key](EventTag("ok"))
  private val secondTagging = Tagging.const[Key](EventTag("second"))

  override def spec: ZSpec[TestEnvironment, Any] = suite("A postgres journal store")(
    testM("Can store and retrieve values from db") {
      (for {
        eventJournal      <- ZIO.service[EventJournalStore[Key, AnEvent]]
        _                 <- eventJournal.append(Key("1"), 0, NonEmptyChunk(FirstEventHappened(1, List("a", "b")), FirstEventHappened(2, Nil))).provide(tagging)
        _                 <- eventJournal.append(Key("1"), 2, NonEmptyChunk(FirstEventHappened(3, Nil))).provide(secondTagging)
        _                 <- eventJournal.append(Key("2"), 0, NonEmptyChunk(FirstEventHappened(5, Nil))).provide(tagging)
        events            <- eventJournal.read(Key("1"), 0).runCollect
        eventsOtherOffset <- eventJournal.read(Key("1"), 2).runCollect
        eventsOtherKey    <- eventJournal.read(Key("2"), 0).runCollect
      } yield (assert(events)(
        equalTo(
          Chunk(
            EntityEvent[Key, AnEvent](Key("1"), 0, FirstEventHappened(1, List("a", "b"))),
            EntityEvent[Key, AnEvent](Key("1"), 1, FirstEventHappened(2, Nil)),
            EntityEvent[Key, AnEvent](Key("1"), 2, FirstEventHappened(3, Nil))
          )
        )
      ) &&
      assert(eventsOtherOffset)(equalTo(Chunk(EntityEvent[Key, AnEvent](Key("1"), 2, FirstEventHappened(3, Nil))))) &&
      assert(eventsOtherKey)(equalTo(Chunk(EntityEvent[Key, AnEvent](Key("2"), 0, FirstEventHappened(5, Nil))))))).provideCustomLayer(layer)
    },
    testM("Can stream all the elements") {
      (for {
        eventJournal <- ZIO.service[EventJournalStore[Key, AnEvent]]
        _            <- eventJournal.append(Key("1"), 0, NonEmptyChunk(FirstEventHappened(1, List("a", "b")), FirstEventHappened(2, Nil))).provide(secondTagging)
        _            <- eventJournal.append(Key("1"), 2, NonEmptyChunk(FirstEventHappened(3, Nil))).provide(tagging)
        _            <- eventJournal.append(Key("2"), 0, NonEmptyChunk(FirstEventHappened(5, Nil))).provide(tagging)
        stream = eventJournal.eventsByTag(EventTag("ok"), None)
        fiber  <- stream.take(2).runCollect.fork
        _      <- TestClock.adjust(200.millis)
        events <- fiber.join
      } yield (assert(events)(
        equalTo(
          Chunk(
            JournalEntry[Long, Key, AnEvent](3, EntityEvent[Key, AnEvent](Key("1"), 2, FirstEventHappened(3, Nil))),
            JournalEntry[Long, Key, AnEvent](4, EntityEvent[Key, AnEvent](Key("2"), 0, FirstEventHappened(5, Nil)))
          )
        )
      )))
        .provideSomeLayer[TestEnvironment](layer)
    }
  ) @@ sequential
}
