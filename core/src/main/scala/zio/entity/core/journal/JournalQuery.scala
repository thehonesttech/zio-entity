package zio.entity.core.journal

import zio.stream.{Stream, ZStream}
import zio._
import zio.entity.core.snapshot.KeyValueStore
import zio.entity.data.{Committable, ConsumerId, EventTag, TagConsumer}

// implementations should commit into offset store
trait JournalQuery[O, K, E] {
  def eventsByTag(tag: EventTag, offset: Option[O]): Stream[Throwable, JournalEntry[O, K, E]]

  def currentEventsByTag(tag: EventTag, offset: Option[O]): Stream[Throwable, JournalEntry[O, K, E]]

}

trait CommittableJournalQuery[O, K, E] {
  def eventsByTag(tag: EventTag, consumerId: ConsumerId): ZStream[Any, Throwable, Committable[JournalEntry[O, K, E]]]

  def currentEventsByTag(tag: EventTag, consumerId: ConsumerId): Stream[Throwable, Committable[JournalEntry[O, K, E]]]

}

class CommittableJournalStore[O, K, E](offsetStore: KeyValueStore[TagConsumer, O], delegateEventJournal: JournalQuery[O, K, E])
    extends CommittableJournalQuery[O, K, E] {

  private def mkCommittableSource[R](
    tag: EventTag,
    consumerId: ConsumerId,
    inner: Option[O] => ZStream[R, Throwable, JournalEntry[O, K, E]]
  ): ZStream[R, Throwable, Committable[JournalEntry[O, K, E]]] = {
    val tagConsumerId = TagConsumer(tag, consumerId)
    stream.Stream
      .fromEffect(Task.unit)
      .mapM { _ =>
        offsetStore.getValue(tagConsumerId)
      }
      .flatMap(inner)
      .map(x => Committable(offsetStore.setValue(tagConsumerId, x.offset), x))
  }

  def eventsByTag(tag: EventTag, consumerId: ConsumerId): ZStream[Any, Throwable, Committable[JournalEntry[O, K, E]]] =
    mkCommittableSource(tag, consumerId, delegateEventJournal.eventsByTag(tag, _))

  def currentEventsByTag(tag: EventTag, consumerId: ConsumerId): Stream[Throwable, Committable[JournalEntry[O, K, E]]] = {
    mkCommittableSource(tag, consumerId, delegateEventJournal.currentEventsByTag(tag, _))
  }
}
