package rdfp.persistence.nestedmapset

import org.scalatest.FlatSpec
import rdfp.persistence.ormlite.mapset.{ PersistentStringMapSet, PersistentSerializingMapSet }
import scala.collection.mutable.{ Stack, Set, Map }
import org.openrdf.model.{ Statement => SesameStatement, ValueFactory }
import org.openrdf.model.impl.ValueFactoryImpl
import rdfp.SetMatcher
import rdfp.SesameRDFStreamProcessor
import com.hp.hpl.jena.util.FileUtils
import rdfp.PersistentMapSetMatcher
import rdfp.persistence.ormlite.nestedmapset.PersistentNestedStringMapSet
import rdfp.persistence.ormlite.nestedmapset.PersistentSerializingNestedMapSet
import org.openrdf.model.{ URI, Resource, Value }

class PersistentSerializingNestedMapSetTest extends PersistentNestedStringMapSetTest {
  override val nestedMapSet = new PersistentSerializingNestedMapSet[String, String, String]("jdbc:h2:mem:")
  val statementNestedMapSet = new PersistentSerializingNestedMapSet[URI, Resource, Value]("jdbc:h2:mem:")
  val Dataset = "data/dnb/dnb-Josef_Spieler-Psychologe.rdf"
  val JosefSpielerURI = "http://d-nb.info/gnd/117483885"

  it should "handle Sesame Statement component objects correctly" in {
    val fac = ValueFactoryImpl.getInstance()
    val s = fac.createStatement(
      fac.createURI("http://d-nb.info/gnd/117483885"),
      fac.createURI("http://xmlns.com/foaf/0.1/name"),
      fac.createLiteral("Josef, Spieler"))
    assert(statementNestedMapSet.size == 0)
    statementNestedMapSet += ((s.getPredicate(), Map(s.getSubject() -> Set(s.getObject()))))
    assert(statementNestedMapSet.size == 1)
    assert(statementNestedMapSet(s.getPredicate()) == Map(s.getSubject() -> Set(s.getObject())))
    assert(statementNestedMapSet(s.getPredicate())(s.getSubject()).contains(s.getObject()))
    statementNestedMapSet(s.getPredicate()) += ((fac.createURI("http://d-nb.info/gnd/117483886"), Set(fac.createLiteral("Spieler, Josef"))))
    assert(statementNestedMapSet.size == 1)
    assert(statementNestedMapSet(s.getPredicate()).size == 2)
    assert(statementNestedMapSet(s.getPredicate()) == Map(s.getSubject() -> Set(s.getObject()),
      fac.createURI("http://d-nb.info/gnd/117483886") -> Set(fac.createLiteral("Spieler, Josef"))))
  }
}
class PersistentNestedStringMapSetTest extends FlatSpec {
  val nestedMapSet: Map[String, Map[String, Set[String]]] = new PersistentNestedStringMapSet("jdbc:h2:mem:")

  "This kind of map" should "return a set with a single value for certain keys." in {
    nestedMapSet += (("subject1", Map("subject1-1" -> Set("subject1-1-1"))))
    assert(nestedMapSet.size == 1)
    assert(nestedMapSet("subject1") == Map("subject1-1" -> Set("subject1-1-1")))
  }
  it should "also be able to handle multiple mappings" in {
    nestedMapSet += (("subject2", Map("subject2-1" -> Set("subject2-1-1"))))
    nestedMapSet += (("subject3", Map("subject3-1" -> Set("subject3-1-1", "subject3-1-2"))))
    assert(nestedMapSet.size == 3)
    assert(nestedMapSet("subject1") == Map("subject1-1" -> Set("subject1-1-1")))
    assert(nestedMapSet("subject2") == Map("subject2-1" -> Set("subject2-1-1")))
    assert(nestedMapSet("subject3") == Map("subject3-1" -> Set("subject3-1-1", "subject3-1-2")))
  }
  it should "update mappings correctly" in {
    nestedMapSet += (("subject1", Map("subject1-1" -> Set("subject1-1-1", "subject1-1-2"))))
    nestedMapSet += (("subject2", Map("subject2-1" -> Set("subject2-1-1", "subject2-1-2", "subject2-1-3"))))
    nestedMapSet += (("subject3", Map("subject3-1" -> Set("subject3-1-1", "subject3-1-2", "subject3-1-3", "subject3-1-4"))))
    nestedMapSet += (("subject4", Map("subject4-1" -> Set("subject4-1-1", "subject4-1-2"))))
    assert(nestedMapSet.size == 4)
    assert(nestedMapSet("subject1") == Map("subject1-1" -> Set("subject1-1-1", "subject1-1-2")))
    assert(nestedMapSet("subject2") == Map("subject2-1" -> Set("subject2-1-1", "subject2-1-2", "subject2-1-3")))
    assert(nestedMapSet("subject3") == Map("subject3-1" -> Set("subject3-1-1", "subject3-1-2", "subject3-1-3", "subject3-1-4")))
    assert(nestedMapSet("subject4") == Map("subject4-1" -> Set("subject4-1-1", "subject4-1-2")))
  }
  it should "remove mappings correctly" in {
    nestedMapSet -= "subject4"
    assert(nestedMapSet.size == 3)
    assert(nestedMapSet.get("subject4") == None)
  }
  it should "return a valid iterator" in {
    val itr = nestedMapSet.iterator
    for (i <- 0 to 3) {
      i match {
        case 0 => assert(itr.hasNext && itr.next ==
          ("subject1", Map("subject1-1" -> Set("subject1-1-1", "subject1-1-2"))))
        case 1 => assert(itr.hasNext && itr.next ==
          ("subject2", Map("subject2-1" -> Set("subject2-1-1", "subject2-1-2", "subject2-1-3"))))
        case 2 => assert(itr.hasNext && itr.next ==
          ("subject3", Map("subject3-1" -> Set("subject3-1-1", "subject3-1-2", "subject3-1-3", "subject3-1-4"))))
        case 3 => assert(!itr.hasNext)
      }
    }
  }
  it should "return a valid iterator for a map for a certain key" in {
    val itr = nestedMapSet("subject3").iterator
    for (i <- 0 to 1) {
      i match {
        case 0 => assert(itr.hasNext && itr.next ==
          ("subject3-1", Set("subject3-1-1", "subject3-1-2", "subject3-1-3", "subject3-1-4")))
        case 1 => assert(!itr.hasNext)
      }
    }
  }
  it should "return a valid iterator for a set for a certain mapping" in {
    val itr = nestedMapSet("subject2")("subject2-1").iterator
    for (i <- 0 to 3) {
      i match {
        case 0 => assert(itr.hasNext && itr.next == "subject2-1-1")
        case 1 => assert(itr.hasNext && itr.next == "subject2-1-2")
        case 2 => assert(itr.hasNext && itr.next == "subject2-1-3")
        case 3 => assert(!itr.hasNext)
      }
    }
  }
  it should "allow modifing a referenced map" in {
    val map = nestedMapSet("subject1")
    assert(map.size == 1)
    map += (("subject1-2", Set("subject1-2-1", "subject1-2-2")))
    map += (("subject1-3", Set("subject1-3-1", "subject1-3-2", "subject1-3-3")))
    assert(map.size == 3)
    assert(nestedMapSet.size == 3)
    assert(nestedMapSet("subject1") == Map("subject1-1" -> Set("subject1-1-1", "subject1-1-2"),
      "subject1-2" -> Set("subject1-2-1", "subject1-2-2"),
      "subject1-3" -> Set("subject1-3-1", "subject1-3-2", "subject1-3-3")))
    map -= "subject1-3"
    assert(map.size == 2)
    assert(nestedMapSet.size == 3)
    assert(nestedMapSet("subject1") == Map("subject1-1" -> Set("subject1-1-1", "subject1-1-2"),
      "subject1-2" -> Set("subject1-2-1", "subject1-2-2")))
  }
  it should "respect the definition of a set when manipulating a referenced value set" in {
    val set = nestedMapSet("subject3")("subject3-1")
    assert(set.size == 4)
    set += "subject3-1-5"
    assert(set.size == 5)
    set += "subject3-1-5"
    assert(set.size == 5)
    set -= "subject3-1-5"
    assert(set.size == 4)
  }
  it should "be able to handle empty maps and empty sets" in {
    assert(nestedMapSet.size == 3)
    nestedMapSet += (("subject5", Map()))
    assert(nestedMapSet.size == 4)
    assert(nestedMapSet("subject5") == Map())
    nestedMapSet += (("subject6", Map("subject6-1" -> Set())))
    assert(nestedMapSet.size == 5)
    assert(nestedMapSet("subject6") == Map("subject6-1" -> Set()))
    nestedMapSet("subject5") += (("subject5-1", Set("subject5-1-1", "subject5-1-2")))
    assert(nestedMapSet.size == 5)
    assert(nestedMapSet("subject5") == Map("subject5-1" -> Set("subject5-1-1", "subject5-1-2")))
  }
  it should "not contain any mappings after removing all mappings" in {
    nestedMapSet.clear
    assert(nestedMapSet.size == 0)
  }
}