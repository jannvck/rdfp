package rdfp.persistence.ormlite.nestedmapset

import scala.collection.mutable.{ Map, Set }
import scala.collection.JavaConverters._
import com.j256.ormlite.dao.{ Dao, DaoManager, ForeignCollection }
import com.j256.ormlite.jdbc.JdbcConnectionSource
import com.j256.ormlite.support.ConnectionSource
import com.j256.ormlite.table.TableUtils
import rdfp.persistence.ormlite.ClosableIteratorWrapper
import rdfp.persistence.SerializingStorage
import rdfp.persistence.ormlite.AbstractForeignCollectionWrapper
import rdfp.persistence.ormlite.map.PersistentStringMap
import rdfp.persistence.ormlite.mapset.PersistentStringMapSet
import rdfp.persistence.ormlite.AbstractForeignCollectionSet

class PersistentSerializingNestedMapSet[K1, K2, V](db: String) extends Map[K1, Map[K2, Set[V]]] with SerializingStorage {
  protected val nestedMapSet = new PersistentNestedStringMapSet(db)
  override def get(key: K1): Option[Map[K2, Set[V]]] = nestedMapSet.get(serializeToString(key)) match {
    case Some(result) => Some(new DeserializingFromStringMapSet(result))
    case _ => None
  }
  override def iterator: Iterator[(K1, Map[K2, Set[V]])] = new Iterator[(K1, Map[K2, Set[V]])] {
    val itr = nestedMapSet.iterator
    override def hasNext = itr.hasNext
    override def next = { val (k, v) = itr.next; (deserializeFromString(k), new DeserializingFromStringMapSet(v)) }
  }
  override def +=(kv: (K1, Map[K2, Set[V]])) = {
    nestedMapSet += ((serializeToString(kv._1), new SerializingToStringMapSet(kv._2)))
    PersistentSerializingNestedMapSet.this
  }
  override def -=(key: K1) = {
    nestedMapSet -= serializeToString(key)
    PersistentSerializingNestedMapSet.this
  }
  override def size = nestedMapSet.size
}
class PersistentNestedStringMapSet(db: String) extends Map[String, Map[String, Set[String]]] {
  protected val connectionSource = new JdbcConnectionSource(db);
  protected val firstMappingDao: Dao[FirstMapping, String] = DaoManager.createDao(connectionSource, classOf[FirstMapping])
  protected val secondMappingDao: Dao[SecondMapping, String] = DaoManager.createDao(connectionSource, classOf[SecondMapping])
  protected val valueDao: Dao[Value, Int] = DaoManager.createDao(connectionSource, classOf[Value])
  TableUtils.createTableIfNotExists(connectionSource, classOf[FirstMapping])
  TableUtils.createTableIfNotExists(connectionSource, classOf[SecondMapping])
  TableUtils.createTableIfNotExists(connectionSource, classOf[Value])
  override def get(key: String): Option[Map[String, Set[String]]] =
    firstMappingDao.queryForId(key) match {
      case result: FirstMapping => Some(new NestedPersistentStringMap(result))
      case _ => None
    }
  override def iterator: Iterator[(String, Map[String, Set[String]])] = {
    class PersistentMapSetIterator extends ClosableIteratorWrapper[(String, Map[String, Set[String]]), FirstMapping](firstMappingDao.iterator()) {
      override def next = {
        val keyResult = itr.next()
        (keyResult.key, new NestedPersistentStringMap(keyResult))
      }
    }
    new PersistentMapSetIterator
  }
  override def +=(kv: (String, Map[String, Set[String]])) = {
    firstMappingDao.queryForId(kv._1) match {
      case result: FirstMapping => {
        removeValuesBy(result)
        for (secondKey <- kv._2.keys) yield {
          val secondMapping = new SecondMapping(result, secondKey)
          result.values.add(secondMapping)
          secondMappingDao.assignEmptyForeignCollection(secondMapping, "values")
          for (valSet <- kv._2.get(secondKey)) yield new ForeignCollectionSet(secondMapping) ++= valSet
        }
      }
      case _ => {
        val firstMapping = new FirstMapping(kv._1)
        firstMappingDao.create(firstMapping)
        firstMappingDao.assignEmptyForeignCollection(firstMapping, "secondKeys")
        for (secondKey <- kv._2.keys) yield {
          val secondMapping = new SecondMapping(firstMapping, secondKey)
          secondMappingDao.create(secondMapping)
          secondMappingDao.assignEmptyForeignCollection(secondMapping, "values")
          new ForeignCollectionSet(secondMapping) ++= kv._2(secondKey)
        }
      }
    }
    PersistentNestedStringMapSet.this
  }
  override def -=(key: String) = {
    firstMappingDao.queryForId(key) match {
      case result: FirstMapping =>
        removeValuesBy(result); firstMappingDao.delete(result)
      case _ =>
    }
    PersistentNestedStringMapSet.this
  }
  override def size = firstMappingDao.countOf().toInt
  override def clear = {
    for (mapping <- new ClosableIteratorWrapper[FirstMapping, FirstMapping](firstMappingDao.iterator) { override def next = itr.next() }) {
      removeValuesBy(mapping)
      firstMappingDao.delete(mapping)
    }
  }
  private def removeValuesBy(mapping: FirstMapping) = {
    // "safe" way: according to java API calling on Iterator.remove() once per call on next() should be okay
    //    val secondMappingItr = mapping.values.iterator()
    //    while (secondMappingItr.hasNext()) {
    //      val valItr = secondMappingItr.next().values.iterator()
    //      while (valItr.hasNext()) {
    //        valItr.next()
    //        valItr.remove()
    //      }
    //      secondMappingItr.remove()
    //    }
    // using DAOs directly: bad practice modifying the data structure while iterating without using remove() on an iterator,
    // but a bit faster use the code above if problems occur
    val secondMappings = new ClosableIteratorWrapper[SecondMapping, SecondMapping](mapping.values.iteratorThrow()) { override def next = itr.next() }
    for (secondMapping <- secondMappings) {
      for (value <- new ClosableIteratorWrapper[Value, Value](secondMapping.values.iteratorThrow()) { override def next = itr.next() })
        valueDao.delete(value)
      secondMappingDao.delete(secondMapping)
    }
  }
  private class NestedPersistentStringMap(first: FirstMapping) extends Map[String, Set[String]] {
    def get(key: String): Option[Set[String]] = {
      secondMappingDao.queryForFieldValues(
        Map("firstKey" -> first.key, "secondKey" -> key.asInstanceOf[Object]).asJava) match {
          case result: java.util.List[SecondMapping] if result.size() > 0 => Some(new ForeignCollectionSet(result.get(0)))
          case _ => None
        }
    }
    def iterator: Iterator[(String, Set[String])] = {
      new ClosableIteratorWrapper[(String, Set[String]), SecondMapping](first.values.iteratorThrow()) {
        override def next = {
          val secondMapping = itr.next(); (secondMapping.secondKey, new ForeignCollectionSet(secondMapping))
        }
      }
    }
    def +=(kv: (String, Set[String])) = {
      secondMappingDao.queryForFieldValues(
        Map("firstKey" -> first.key, "secondKey" -> kv._1.asInstanceOf[Object]).asJava) match {
          case result: java.util.List[SecondMapping] if result.size() > 0 => {
            val secondMapping = result.get(0)
            removeValuesBy(secondMapping)
            new ForeignCollectionSet(secondMapping) ++= kv._2
          }
          case _ => {
            val secondMapping = new SecondMapping(first, kv._1)
            secondMappingDao.create(secondMapping)
            secondMappingDao.assignEmptyForeignCollection(secondMapping, "values")
            new ForeignCollectionSet(secondMapping) ++= kv._2
          }
        }
      this
    }
    def -=(key: String) = {
      secondMappingDao.queryForFieldValues(
        Map("firstKey" -> first.key, "secondKey" -> key.asInstanceOf[Object]).asJava) match {
          case result: java.util.List[SecondMapping] if result.size() > 0 =>
            val secondMapping = result.get(0);
            removeValuesBy(secondMapping); secondMappingDao.delete(secondMapping)
          case _ =>
        }
      this
    }
    private def removeValuesBy(mapping: SecondMapping) = {
      for (v <- new ClosableIteratorWrapper[Value, Value](mapping.values.iteratorThrow()) { override def next = itr.next() })
        valueDao.delete(v)
    }
  }
  private class ForeignCollectionSet(second: SecondMapping) extends AbstractForeignCollectionSet[SecondMapping, Value, String](second) {
    override def contains(key: String): Boolean = !queryForValue(key).isEmpty()
    override def +=(elem: String) = { if (!contains(elem)) fc.add(new Value(second, elem)); this }
    override def -=(elem: String) = {
      queryForValue(elem) match {
        case result: java.util.List[Value] if result.size > 0 => valueDao.delete(result)
        case _ =>
      }
      this
    }
    override def size = valueDao.queryForEq("secondKey", second).size // TODO: not sure about scalability... (maybe better use default: lazy iterator)
    private def queryForValue(value: String) = valueDao.queryForFieldValues(Map("secondKey" -> second, "value" -> value.asInstanceOf[Object]).asJava)
  }
}