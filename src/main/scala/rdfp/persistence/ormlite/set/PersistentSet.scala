package rdfp.persistence.ormlite.set

import scala.collection.mutable.Set
import com.j256.ormlite.dao.{ Dao, DaoManager, ForeignCollection, CloseableIterator }
import com.j256.ormlite.jdbc.JdbcConnectionSource
import com.j256.ormlite.support.ConnectionSource
import com.j256.ormlite.table.TableUtils
import rdfp.persistence.ormlite.ClosableIteratorWrapper
import rdfp.persistence.SerializingStorage

class PersistentSerializingSet[T](db: String) extends Set[T] with SerializingStorage {
  protected val stringSet = new PersistentStringSet(db)
  override def contains(key: T): Boolean = stringSet.contains(serializeToString(key))
  override def iterator: Iterator[T] = new DeserializingFromStringIterator[T](stringSet.iterator)
  override def +=(elem: T) = { stringSet += serializeToString(elem); PersistentSerializingSet.this }
  override def -=(elem: T) = { stringSet -= serializeToString(elem); PersistentSerializingSet.this }
  override def size: Int = stringSet.size
}
class PersistentStringSet(db: String) extends Set[String] {
  protected val connectionSource = new JdbcConnectionSource(db);
  protected val elemDao: Dao[Element, String] = DaoManager.createDao(connectionSource, classOf[Element])
  TableUtils.createTableIfNotExists(connectionSource, classOf[Element])
  override def contains(key: String): Boolean =
    elemDao.queryForId(key) match {
      case elem: Element => true
      case _ => false
    }
  override def iterator: Iterator[String] = {
    class PersistentSetIterator extends ClosableIteratorWrapper[String, Element](elemDao.iterator()) {
      override def next = itr.next.element
    }
    new PersistentSetIterator
  }
  override def +=(elem: String) = {
    if (!contains(elem))
      elemDao.create(new Element(elem))
    this
  }
  override def -=(elem: String) = {
    if (contains(elem))
      elemDao.deleteById(elem)
    this
  }
  override def size: Int = elemDao.countOf().toInt
}