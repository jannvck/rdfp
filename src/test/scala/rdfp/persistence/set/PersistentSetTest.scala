package rdfp.persistence.set

import org.scalatest.FlatSpec
import rdfp.persistence.ormlite.set.PersistentSerializingSet
import scala.collection.mutable.Stack
import scala.collection.mutable.Set
import rdfp.persistence.ormlite.set.PersistentStringSet

class PersistentSetTest extends PersistentStringSetTest {
  override val set = new PersistentSerializingSet[String]("jdbc:h2:mem:")
}

class PersistentStringSetTest extends FlatSpec {
  val set: Set[String] = new PersistentStringSet("jdbc:h2:mem:")
  
  "A PersistentSet" should "contain an element after adding it" in {
    assert(set.size == 0)
    set += "subject1"
    assert(set.size == 1 && set.contains("subject1"))
  }
  it should "ignore adding an element already contained in this set" in {
    set += "subject1"
    assert(set.size == 1 && set.contains("subject1"))
  }
  it should "allow adding multiple elements at once" in {
    set ++= Set("subject2", "subject3", "subject4")
    assert(set.size == 4 && 
        set.contains("subject1") && 
        set.contains("subject2") && 
        set.contains("subject3") &&
        set.contains("subject4"))
  }
  it should "not contain an element anymore after removing it" in {
    set -= "subject4"
    assert(set.size == 3 && 
        set.contains("subject1") && 
        set.contains("subject2") && 
        set.contains("subject3") &&
        !set.contains("subject4"))
  }
  it should "report false upon calling contain() on a nonexistant element" in {
    assert(!set.contains("some nonexistant element "+Math.random()))
  }
  it should "ignore removing nonexistant elements" in {
    assert(set.size == 3)
    set -= "some nonexistant element "+Math.random()
    assert(set.size == 3)
  }
  it should "return an iterator to iterate over element in this set" in {
    val itr = set.iterator
    for(i <- 0 to 3) {
      i match {
        case 0 => assert(itr.hasNext && itr.next == "subject1")
        case 1 => assert(itr.hasNext && itr.next == "subject2")
        case 2 => assert(itr.hasNext && itr.next == "subject3")
        case 3 => assert(!itr.hasNext)
      }
    }
  }
  it should "not contain anything after calling clear()" in {
    set.clear
    assert(set.isEmpty)
  }
}