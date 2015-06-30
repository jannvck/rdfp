package rdfp.persistence.ormlite.map

import scala.collection.mutable.{ Map, Set }
import scala.collection.JavaConverters._
import com.j256.ormlite.dao.{ Dao, DaoManager, ForeignCollection }
import com.j256.ormlite.jdbc.JdbcConnectionSource
import com.j256.ormlite.support.ConnectionSource
import com.j256.ormlite.table.TableUtils
import rdfp.persistence.ormlite.ClosableIteratorWrapper
import rdfp.persistence.SerializingStorage

class PersistentSerializingMap[K, V](db: String) extends Map[K, V] with SerializingStorage {
  protected val stringMap = new PersistentStringMap(db)
  override def get(key: K): Option[V] = {
    stringMap.get(serializeToString[K](key)) match {
      case Some(s) => Some(deserializeFromString[V](s))
      case None => None
    }
  }
  override def iterator: Iterator[(K, V)] = {
    new Iterator[(K, V)] {
      val itr = stringMap.iterator
      override def hasNext = itr.hasNext
      override def next = {
        val (k, v) = itr.next
        (deserializeFromString[K](k), deserializeFromString[V](v))
      }
    }
  }
  override def +=(kv: (K, V)) = {
    stringMap += ((serializeToString[K](kv._1), serializeToString[V](kv._2)))
    PersistentSerializingMap.this
  }
  override def -=(key: K) = {
    stringMap -= serializeToString[K](key)
    PersistentSerializingMap.this
  }
  override def size = stringMap.size
}
class PersistentStringMap(db: String) extends Map[String, String] {
  protected val connectionSource = new JdbcConnectionSource(db)
  protected val mappingDao: Dao[Mapping, String] = DaoManager.createDao(connectionSource, classOf[Mapping])
  TableUtils.createTableIfNotExists(connectionSource, classOf[Mapping])
  override def get(key: String): Option[String] =
    mappingDao.queryForId(key) match {
      case result: Mapping => Some(result.value)
      case _ => None
    }
  override def iterator: Iterator[(String, String)] =
    new ClosableIteratorWrapper[(String, String), Mapping](mappingDao.iterator) {
      def next = { val mapping = itr.next(); (mapping.key, mapping.value) }
    }
  override def +=(kv: (String, String)) = {
    mappingDao.queryForId(kv._1) match {
      case result: Mapping => mappingDao.delete(result)
      case _ =>
    }
    val key = new Mapping(kv._1, kv._2)
    mappingDao.create(key)
    PersistentStringMap.this
  }
  override def -=(key: String) = {
    mappingDao.queryForId(key) match {
      case result: Mapping => mappingDao.deleteById(key)
      case _ =>
    }
    PersistentStringMap.this
  }
  override def size = mappingDao.countOf().toInt
}