package rdfp

import scala.collection.mutable.{ ListBuffer, Set, Map, HashMap }
import rdfp.persistence.ormlite.set.{ PersistentStringSet, PersistentSerializingSet }
import rdfp.persistence.ormlite.map.{ PersistentStringMap, PersistentSerializingMap }
import rdfp.persistence.ormlite.mapset.{ PersistentStringMapSet, PersistentSerializingMapSet }
import rdfp.persistence.ormlite.nestedmapset.{ PersistentNestedStringMapSet, PersistentSerializingNestedMapSet }

trait Matcher[S] {
  def matches: S => Boolean
  def handle: S => Option[_]
  def transform: S => Option[Set[S]]
}
abstract class AbstractMatcher[S, H](mtchr: S => Boolean, op: S => Option[H], tr: S => Option[Set[S]]) extends Matcher[S] {
  protected def implOp: H => Any
  override def matches = mtchr
  override def handle = (s: S) => op(s) match { case Some(h) => implOp(h); Some(h) case None => None }
  override def transform = tr
}
class DefaultMatcher[S, H] extends AbstractMatcher[S, H]((s: S) => true, (s: S) => None, (s: S) => Some(Set(s))) {
  override protected def implOp = (h: H) => {}
}
class ListMatcher[S, H](m: S => Boolean, add: S => Option[H], t: S => Option[Set[S]]) extends AbstractMatcher[S, H](m, add, t) {
  val list: ListBuffer[H] = ListBuffer[H]()
  override protected def implOp = list.+= _
}
class SetMatcher[S, H](m: S => Boolean, add: S => Option[H], t: S => Option[Set[S]]) extends AbstractMatcher[S, H](m, add, t) {
  val set: Set[H] = Set[H]()
  override protected def implOp = set.+= _
}
class MapMatcher[S, K, V](m: S => Boolean, put: S => Option[(K, V)], t: S => Option[Set[S]]) extends AbstractMatcher[S, (K, V)](m, put, t) {
  val map: Map[K, V] = HashMap[K, V]()
  override protected def implOp = map.+= _
}
class MapSetMatcher[S, K, V](m: S => Boolean, put: S => Option[(K, V)], t: S => Option[Set[S]]) extends AbstractMatcher[S, (K, V)](m, put, t) {
  val map: Map[K, Set[V]] = HashMap[K, Set[V]]()
  override protected def implOp = (m: (K, V)) => (map getOrElseUpdate (m._1, Set(m._2))) += m._2
}
class NestedMapSetMatcher[S, K1, K2, V](m: S => Boolean, put: S => Option[(K1, K2, V)], t: S => Option[Set[S]]) extends AbstractMatcher[S, (K1, K2, V)](m, put, t) {
  val map: Map[K1, Map[K2, Set[V]]] = HashMap[K1, Map[K2, Set[V]]]()
  override protected def implOp = (m: (K1, K2, V)) => ((map.getOrElseUpdate(m._1, Map(m._2 -> Set(m._3)))).getOrElseUpdate(m._2, Set(m._3))) += m._3
}
class PersistentSetMatcher[S, V](db: String, m: S => Boolean, add: S => Option[V], t: S => Option[Set[S]]) extends SetMatcher[S, V](m, add, t) {
  override val set: Set[V] = new PersistentSerializingSet[V](db)
}
class PersistentStringSetMatcher[S](db: String, m: S => Boolean, add: S => Option[String], t: S => Option[Set[S]]) extends SetMatcher[S, String](m, add, t) {
  override val set: Set[String] = new PersistentStringSet(db)
}
class PersistentMapMatcher[S, K, V](db: String, m: S => Boolean, put: S => Option[(K, V)], t: S => Option[Set[S]]) extends MapMatcher[S, K, V](m, put, t) {
  override val map: Map[K, V] = new PersistentSerializingMap[K, V](db)
}
class PersitentStringMapMatcher[S](db: String, m: S => Boolean, put: S => Option[(String, String)], t: S => Option[Set[S]]) extends PersistentMapMatcher[S, String, String](db, m, put, t) {
  override val map: Map[String, String] = new PersistentStringMap(db)
}
class PersistentMapSetMatcher[S, K, V](db: String, m: S => Boolean, put: S => Option[(K, V)], t: S => Option[Set[S]]) extends MapSetMatcher[S, K, V](m, put, t) {
  override val map: Map[K, Set[V]] = new PersistentSerializingMapSet[K, V](db)
}
class PersistentStringMapSetMatcher[S](db: String, m: S => Boolean, put: S => Option[(String, String)], t: S => Option[Set[S]]) extends PersistentMapSetMatcher[S, String, String](db, m, put, t) {
  override val map = new PersistentSerializingMapSet[String, String](db)
}
class PersistentNestedMapSetMatcher[S, K1, K2, V](db: String, m: S => Boolean, put: S => Option[(K1, K2, V)], t: S => Option[Set[S]]) extends NestedMapSetMatcher[S, K1, K2, V](m, put, t) {
  override val map: Map[K1, Map[K2, Set[V]]] = new PersistentSerializingNestedMapSet[K1, K2, V](db)
}
class PersistentNestedStringMapSetMatcher[S](db: String, m: S => Boolean, put: S => Option[(String, String, String)], t: S => Option[Set[S]]) extends PersistentNestedMapSetMatcher[S, String, String, String](db, m, put, t) {
  override val map: Map[String, Map[String, Set[String]]] = new PersistentNestedStringMapSet(db)
}