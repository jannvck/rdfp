package rdfp.persistence

import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import scala.collection.mutable.{ Set, Map }
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.output.ByteArrayOutputStream
import rdfp.persistence.ormlite.mapset.PersistentStringMapSet
import rdfp.persistence.ormlite.mapset.PersistentSerializingMapSet

trait SerializingStorage {
  protected def serialize[T](o: T): Array[Byte] = {
    val bos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(bos)
    oos.writeObject(o)
    oos.close()
    bos.toByteArray()
  }
  protected def deserialize[T](o: Array[Byte]): T = {
    val bis = new ByteArrayInputStream(o)
    val ois = new ObjectInputStream(bis)
    val obj = ois.readObject().asInstanceOf[T]
    ois.close()
    obj
  }
  protected def serializeToString[T](s: T): String = Base64.encodeBase64String(serialize[T](s))
  protected def deserializeFromString[T](s: String): T = deserialize[T](Base64.decodeBase64(s))
  protected class DeserializingFromStringSet[T](set: Set[String]) extends Set[T] {
    override def contains(key: T) = set.contains(serializeToString(key))
    override def +=(elem: T) = { set += serializeToString(elem); this }
    override def -=(elem: T) = { set -= serializeToString(elem); this }
    override def iterator = new DeserializingFromStringIterator(set.iterator)
    override def size = set.size
  }
  protected class SerializingToStringSet[T](set: Set[T]) extends Set[String] {
    override def contains(key: String) = set.contains(deserializeFromString(key))
    override def +=(elem: String) = { set += deserializeFromString(elem); this }
    override def -=(elem: String) = { set -= deserializeFromString(elem); this }
    override def iterator = new SerializingToStringIterator(set.iterator)
    override def size = set.size
  }
  protected class DeserializingFromStringMap[K, V](map: Map[String, String]) extends Map[K, V] {
    override def get(key: K): Option[V] = map.get(serializeToString(key)) match {
      case Some(result) => Some(deserializeFromString(result))
      case _ => None
    }
    override def iterator: Iterator[(K, V)] = new Iterator[(K, V)] {
      val itr = DeserializingFromStringMap.this.map.iterator
      override def hasNext = itr.hasNext
      override def next = { val (k, v) = itr.next; (deserializeFromString(k), deserializeFromString(v)) }
    }
    override def +=(kv: (K, V)) = { map += ((serializeToString(kv._1), serializeToString(kv._2))); this }
    override def -=(key: K) = { map -= serializeToString(key); this }
    override def size = map.size
  }
  protected class DeserializingFromStringMapSet[K, V](map: Map[String, Set[String]]) extends Map[K, Set[V]] {
    override def get(key: K): Option[Set[V]] = map.get(serializeToString(key)) match {
      case Some(result) => Some(new DeserializingFromStringSet(result))
      case _ => None
    }
    override def iterator: Iterator[(K, Set[V])] = new Iterator[(K, Set[V])] {
      val itr = DeserializingFromStringMapSet.this.map.iterator
      override def hasNext = itr.hasNext
      override def next = { val (k, v) = itr.next; (deserializeFromString(k), new DeserializingFromStringSet(v))}
    }
    override def +=(kv: (K, Set[V])) = { map += ((serializeToString(kv._1), new SerializingToStringSet(kv._2))); this }
    override def -=(key: K) = { map -= serializeToString(key); this }
    override def size = map.size
  }
  protected class SerializingToStringMapSet[K,V](map: Map[K, Set[V]]) extends Map[String, Set[String]] {
    override def get(key: String): Option[Set[String]] = map.get(deserializeFromString(key)) match {
      case Some(result) => Some(new SerializingToStringSet[V](result))
      case _ => None
    }
    override def iterator: Iterator[(String, Set[String])] = new Iterator[(String, Set[String])] {
      val itr = SerializingToStringMapSet.this.map.iterator
      override def hasNext = itr.hasNext
      override def next = { val (k, v) = itr.next; (serializeToString(k), new SerializingToStringSet(v))}
    }
    override def +=(kv: (String, Set[String])) = { map += ((deserializeFromString(kv._1), new DeserializingFromStringSet(kv._2))); this }
    override def -=(key: String) = { map -= deserializeFromString(key); this }
    override def size = map.size
  }
  protected class DeserializingFromStringIterator[T](itr: Iterator[String]) extends Iterator[T] {
    override def hasNext = itr.hasNext
    override def next = deserializeFromString[T](itr.next)
  }
  protected class SerializingToStringIterator[T](itr: Iterator[T]) extends Iterator[String] {
    override def hasNext = itr.hasNext
    override def next = serializeToString(itr.next)
  }
}
