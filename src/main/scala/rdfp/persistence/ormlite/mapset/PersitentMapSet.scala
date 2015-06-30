package rdfp.persistence.ormlite.mapset

import scala.collection.mutable.{ Map, Set }
import scala.collection.JavaConverters._
import com.j256.ormlite.dao.{ Dao, DaoManager, ForeignCollection }
import com.j256.ormlite.jdbc.JdbcConnectionSource
import com.j256.ormlite.support.ConnectionSource
import com.j256.ormlite.table.TableUtils
import rdfp.persistence.ormlite.ClosableIteratorWrapper
import rdfp.persistence.SerializingStorage

class PersistentSerializingMapSet[K, V](db: String) extends Map[K, Set[V]] with SerializingStorage {
  protected val stringMapSet = new PersistentStringMapSet(db)
  override def get(key: K): Option[Set[V]] = {
    stringMapSet.get(serializeToString(key)) match {
      case Some(s) => Some(new DeserializingFromStringSet(s))
      case None => None
    }
  }
  override def iterator: Iterator[(K, Set[V])] = {
    new Iterator[(K, Set[V])] {
      val itr = stringMapSet.iterator
      override def hasNext = itr.hasNext
      override def next = {
        val (k, v) = itr.next
        (deserializeFromString(k), new DeserializingFromStringSet(v))
      }
    }
  }
  override def +=(kv: (K, Set[V])) = {
    val serStrSet = Set[String]()
    for (elem <- kv._2)
      serStrSet += serializeToString(elem)
    stringMapSet += ((serializeToString(kv._1), serStrSet))
    PersistentSerializingMapSet.this
  }
  override def -=(key: K) = {
    stringMapSet -= serializeToString(key)
    PersistentSerializingMapSet.this
  }
  override def size = stringMapSet.size
}
class PersistentStringMapSet(db: String) extends Map[String, Set[String]] {
  protected val connectionSource = new JdbcConnectionSource(db)
  protected val keyDao: Dao[Key, String] = DaoManager.createDao(connectionSource, classOf[Key])
  protected val valueDao: Dao[Value, Int] = DaoManager.createDao(connectionSource, classOf[Value])
  TableUtils.createTableIfNotExists(connectionSource, classOf[Key])
  TableUtils.createTableIfNotExists(connectionSource, classOf[Value])
  override def get(key: String): Option[Set[String]] =
    keyDao.queryForId(key) match {
      case result: Key => Some(new ForeignCollectionSet(result))
      case _ => None
    }
  override def iterator: Iterator[(String, Set[String])] = {
    class PersistentMapSetIterator extends ClosableIteratorWrapper[(String, Set[String]), Key](keyDao.iterator()) {
      override def next = {
        val keyResult = itr.next()
        (keyResult.key, new ForeignCollectionSet(keyResult))
      }
    }
    new PersistentMapSetIterator
  }
  override def +=(kv: (String, Set[String])) = {
    keyDao.queryForId(kv._1) match {
      case result: Key => {
        val values = new ForeignCollectionSet(result)
        values.clear
        values ++= kv._2
      }
      case _ => {
        val key = new Key(kv._1)
        keyDao.create(key)
        keyDao.assignEmptyForeignCollection(key, "values")
        new ForeignCollectionSet(key) ++= kv._2
      }
    }
    PersistentStringMapSet.this
  }
  override def -=(key: String) = {
    keyDao.queryForId(key) match {
      case result: Key => {
        result.values.clear
        keyDao.deleteById(key)
      }
      case _ =>
    }
    PersistentStringMapSet.this
  }
  override def size = keyDao.countOf().toInt
  protected class ForeignCollectionSet(k: Key) extends Set[String] {
    private val fc = k.values
    override def contains(key: String): Boolean = !queryForValue(key).isEmpty()
    override def iterator: Iterator[String] = new ClosableIteratorWrapper[String, Value](fc.closeableIterator()) {
      override def next = itr.next.value
    }
    override def +=(elem: String) = { if (!contains(elem)) fc.add(new Value(k, elem)); this }
    override def -=(elem: String) = {
      queryForValue(elem) match {
        case result: java.util.List[Value] if result.size > 0 => valueDao.delete(result)
        case _ =>
      }
      this
    }
    override def clear = fc.clear()
    override def size = fc.size()
    private def queryForValue(value: String) = valueDao.queryForFieldValues(Map("key" -> k.key, "value" -> value.asInstanceOf[Object]).asJava)
  }
}