package rdfp.persistence.ormlite
import com.j256.ormlite.dao.{ CloseableIterator, ForeignCollection }
import scala.collection.mutable.{ IndexedSeq, Set, Map }

protected abstract class ClosableIteratorWrapper[Outer, Inner](i: CloseableIterator[Inner]) extends Iterator[Outer] {
  protected val itr = i
  override def hasNext = itr.hasNext
  override def finalize = itr.close()
}
//protected trait HavingKey[Key] {
//  val key: Key
//}
protected trait HavingValue[Value] {
  val value: Value
}
protected trait HavingForeignCollection[Value] {
  val values: ForeignCollection[Value]
}
//protected trait HavingMapping[K,V] extends HavingKey[K] with HavingValue[V]
//protected trait HavingMappingToForeignCollection[K,FC] extends HavingKey[K] with HavingForeignCollection[FC]
protected abstract class AbstractForeignCollectionWrapper[Key <: HavingForeignCollection[InnerValue], InnerValue <: HavingValue[OuterValue], OuterValue](k: Key)
  extends Iterable[OuterValue] {
  protected val fc = k.values
  override def iterator: Iterator[OuterValue] = new ClosableIteratorWrapper[OuterValue, InnerValue](fc.closeableIterator()) {
    override def next = itr.next.value
  }
}
protected abstract class ForeignCollectionSeqWrapper[Key <: HavingForeignCollection[InnerValue], InnerValue <: HavingValue[OuterValue], OuterValue](k: Key)
  extends AbstractForeignCollectionWrapper[Key, InnerValue, OuterValue](k) with IndexedSeq[OuterValue] {
  override def size = fc.size()
  override def length = size
}
protected abstract class AbstractForeignCollectionSet[Key <: HavingForeignCollection[InnerValue], InnerValue <: HavingValue[OuterValue], OuterValue](k: Key)
  extends AbstractForeignCollectionWrapper[Key, InnerValue, OuterValue](k) with Set[OuterValue] {
  override def size = fc.size()
  override def clear = fc.clear()
}
//protected trait MapWrapper[OuterKey, OuterValue, InnerKey <: HavingMapping[OuterKey,OuterValue], InnerValue  <: HavingValue[OuterValue]] extends Map[OuterKey, OuterValue]
//protected trait MapSetWrapper[OuterKey, OuterValue, InnerKey <: HavingMappingToForeignCollection[OuterKey,InnerValue], InnerValue  <: HavingValue[OuterValue]] extends Map[OuterKey, Set[OuterValue]]