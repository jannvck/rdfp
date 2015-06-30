package rdfp.persistence.mapset

import org.scalatest.FlatSpec
import rdfp.persistence.ormlite.mapset.{ PersistentStringMapSet, PersistentSerializingMapSet }
import scala.collection.mutable.{ Stack, Set, Map }
import org.openrdf.model.{ Statement => SesameStatement, ValueFactory }
import org.openrdf.model.impl.ValueFactoryImpl
import rdfp.SetMatcher
import rdfp.SesameRDFStreamProcessor
import com.hp.hpl.jena.util.FileUtils
import rdfp.PersistentMapSetMatcher

class PersistentMapSetTest extends PersistentStringMapSetTest {
  override val mapSet = new PersistentSerializingMapSet[String, String]("jdbc:h2:mem:")
  val statementMapSet = new PersistentSerializingMapSet[String, SesameStatement]("jdbc:h2:mem:")
  val Dataset = "data/dnb/dnb-Josef_Spieler-Psychologe.rdf"
  val JosefSpielerURI = "http://d-nb.info/gnd/117483885"

  it should "handle a Sesame Statement object correctly" in {
    val fac = ValueFactoryImpl.getInstance()
    val s = fac.createStatement(
      fac.createURI("http://d-nb.info/gnd/117483885"),
      fac.createURI("http://xmlns.com/foaf/0.1/name"),
      fac.createLiteral("Josef, Spieler"))
    assert(statementMapSet.size == 0)
    statementMapSet += (("statementKey1", Set(s)))
    assert(statementMapSet.size == 1)
    assert(statementMapSet("statementKey1") == Set(s))
  }
}

class PersistentStringMapSetTest extends FlatSpec {
  val mapSet: Map[String, Set[String]] = new PersistentStringMapSet("jdbc:h2:mem:")

  "This kind of map" should "return a set with a single value " +
    "for a certain key." in {
      mapSet += (("subject1", Set("subject1-1")))
      assert(mapSet("subject1") == Set("subject1-1"))
    }
  it should "return a set with multiple values for a certain key." in {
    mapSet += (("subject2", Set("subject2-1", "subject2-2")))
    assert(mapSet("subject2") == Set("subject2-1", "subject2-2"))
  }
  it should "return an iterator to iterate over the mappings" in {
    val itr = mapSet.iterator
    for (i <- 0 to 2) {
      i match {
        case 0 => assert(itr.hasNext && itr.next ==
          ("subject1", Set("subject1-1")))
        case 1 => assert(itr.hasNext && itr.next ==
          ("subject2", Set("subject2-1", "subject2-2")))
        case 2 => assert(!itr.hasNext)
      }
    }
  }
  it should "handle getOrElseUpdate() calls correctly" in {
    assert(mapSet("subject2") == Set("subject2-1", "subject2-2"))
    // add to existing value set
    mapSet.getOrElseUpdate("subject2", Set("subject2-3")) += "subject2-3"
    assert(mapSet("subject2") == Set("subject2-1", "subject2-2", "subject2-3"))
    // create new mapping with new value set
    mapSet.getOrElseUpdate("subject3", Set("subject3-1")) += "subject3-1"
    assert(mapSet("subject3") == Set("subject3-1"))
  }
  it should "handle contains() calls correctly" in {
    assert(mapSet contains "subject1")
    assert(mapSet contains "subject2")
    assert(mapSet contains "subject3")
  }
  it should "remove a mapping correctly" in {
    assert(!((mapSet -= "subject1") contains "subject1"))
  }
  it should "not remove other mappings upon removing a single one" in {
    assert(mapSet contains "subject2")
    assert(mapSet contains "subject3")
  }
  it should "update a mapping if the key already exists" in {
    assert(mapSet("subject3") == Set("subject3-1"))
    mapSet += (("subject3", Set("subject3-1", "subject3-2")))
    assert(mapSet("subject3") == Set("subject3-1", "subject3-2"))
  }
  it should "return the correct size of a value set" in {
    assert(mapSet("subject3").size == 2)
  }
  it should "not check for a value in all value sets if calling contains on a single value set" in {
    // assert map structure
    assert(mapSet.size == 2)
    assert(mapSet("subject2") == Set("subject2-1", "subject2-2", "subject2-3"))
    assert(mapSet("subject3") == Set("subject3-1", "subject3-2"))
    // purpose of this test: underlying database backend needs to respect mappings
    assert(!mapSet("subject2").contains("subject3-1"))
    assert(!mapSet("subject3").contains("subject2-2"))
  }
  it should "allow maniupulating a value set" in {
    val set = mapSet("subject2")
    assert(set.size == 3)
    set += "subject2-4"
    assert(set.size == 4)
    set += "subject2-4"
    assert(set.size == 4)
    set -= "subject2-4"
    assert(set.size == 3)
  }
  it should "not contain any mappings after clearing" in {
    mapSet.clear
    assert(mapSet isEmpty)
  }
  it should "throw an Exception if no mapping has been found" in {
    assert(mapSet.get("subject1") == None)
    intercept[java.util.NoSuchElementException] {
      assert(mapSet("subject1").isEmpty)
    }
  }
}