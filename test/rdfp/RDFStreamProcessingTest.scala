package rdfp

import org.scalatest.FlatSpec
import org.apache.jena.riot.out.SinkTripleOutput
import org.apache.jena.riot.system.SyntaxLabels
import com.hp.hpl.jena.graph.{ NodeFactory, Triple, Node }
import com.hp.hpl.jena.util.FileUtils
import java.io.BufferedReader
import java.io.FileReader
import org.openrdf.model.{ Statement => SesameStatement, BNode, URI, Value, Resource }
import java.io.File
import scala.actors.OutputChannel
import scala.collection.mutable.{ Set, ListBuffer }
import scala.actors.Actor

class RDFStreamProcessingTest extends FlatSpec {
  val Dataset = "data/pool/dnb/dnb-Josef_Spieler-Psychologe.rdf"
  val JosefSpielerURI = "http://d-nb.info/gnd/117483885"

  val jenaMatcher = new SetMatcher(
    (t: Triple) => t.getSubject().hasURI(JosefSpielerURI), // match condition
    (t: Triple) => Some(t.getObject()), // element to store in this Matcher's list
    (t: Triple) => Some(Set(t))) // triple to store, the place for transformations

  "A JenaRDFStreamProcessor" should "return 16 objects for a " +
    "test dataset and matcher, running serial" in {
      new JenaRDFStreamProcessor(
        Dataset, // source
        (t: Triple) => None, // sink
        List(jenaMatcher)).trigger
      assert(jenaMatcher.set.size == 16)
    }
  it should "return 16 triples for a test dataset and matcher if run multi-threaded" in {
    val consumers = Set[Actor]()
    val consumedStatements = Set[Statement[Triple]]()
    val processor = new JenaRDFStreamProcessor(
      Dataset,
      (t: Triple) => consumers.foreach((c: Actor) => c ! Statement(t)), // send matched triple to all consumers
      List(jenaMatcher))
    val producer = processor.producer
    consumers += processor.consumer((t: Statement[Triple]) => consumedStatements += t)
    producer ! Start
    Thread.sleep(100)
    assert(consumedStatements.size == 16)
  }
  it should "return 16 triples for a test dataset and matcher, collecting a total of 18 triples" +
    "in a single consumer, because of two unique triples in each thread if run multi-threaded" in {
      val consumers = Set[Actor]()
      val consumedStatements = Set[Statement[Triple]]()
      val processor = new JenaRDFStreamProcessor(
        Dataset,
        (t: Triple) => consumers.foreach((c: Actor) => c ! Statement(t)), // send matched triple to all consumers
        List(jenaMatcher))
      val producer0 = processor.producer
      val producer1 = processor.producer
      consumers += processor.consumer((t: Statement[Triple]) => consumedStatements += t)
      producer0 ! Start
      producer1 ! Start
      Thread.sleep(100)
      assert(consumedStatements.size == 18)
    }
  it should "return 16 triples for a test dataset and matcher for each parser thread, " +
    "resulting in a total of 32 triples sent to a single consumer" in {
      val consumers = Set[Actor]()
      val consumedStatements = ListBuffer[Statement[Triple]]()
      val processor = new JenaRDFStreamProcessor(
        Dataset,
        (t: Triple) => consumers.foreach((c: Actor) => c ! Statement(t)), // send matched triple to all consumers
        List(jenaMatcher))
      val producer0 = processor.producer
      val producer1 = processor.producer
      consumers += processor.consumer((t: Statement[Triple]) => consumedStatements += t)
      producer0 ! Start
      producer1 ! Start
      Thread.sleep(100)
      assert(consumedStatements.size == 32)
    }

  val sesameMatcher = new SetMatcher(
    (s: SesameStatement) => s.getSubject().stringValue().equals(JosefSpielerURI), // match condition
    (s: SesameStatement) => Some(s.getObject()), // element to store in this Matcher's list
    (s: SesameStatement) => Some(Set(s))) // triple to store, the place for transformations

  "A SesameRDFStreamProcessor" should "return 16 objects for this " +
    "particular dataset and matcher" in {
      SesameRDFStreamProcessor(
        () => FileUtils.openResourceFile(Dataset),
        (s: SesameStatement) => None,
        List(sesameMatcher)).trigger
      assert(sesameMatcher.set.size == 16)
    }
  it should "return 16 statements for this particular dataset and matcher if run multi-threaded" in {
    val consumers = Set[Actor]()
    val consumedStatements = Set[Statement[SesameStatement]]()
    val processor = SesameRDFStreamProcessor(
      () => FileUtils.openResourceFile(Dataset),
      (s: SesameStatement) => consumers.foreach((c: Actor) => c ! Statement(s)), // send matched triple to all consumers
      List(sesameMatcher))
    val producer = processor.producer
    consumers += processor.consumer((s: Statement[SesameStatement]) => consumedStatements += s)
    producer ! Start
    Thread.sleep(100)
    assert(consumedStatements.size == 16)
  }
  it should "return 16 statements for a test dataset and matcher, collecting a total of 18 statements" +
    "in a single consumer, because of two unique triples in each thread if run multi-threaded" in {
      val consumers = Set[Actor]()
      val consumedStatements = Set[Statement[SesameStatement]]()
      val processor = SesameRDFStreamProcessor(
        () => FileUtils.openResourceFile(Dataset),
        (s: SesameStatement) => consumers.foreach((c: Actor) => c ! Statement(s)), // send matched triple to all consumers
        List(sesameMatcher))
      val producer0 = processor.producer
      val producer1 = processor.producer
      consumers += processor.consumer((s: Statement[SesameStatement]) => consumedStatements += s)
      producer0 ! Start
      producer1 ! Start
      Thread.sleep(100)
      assert(consumedStatements.size == 18)
    }
  it should "return 16 triples for a test dataset and matcher for each parser thread, " +
    "resulting in a total of 32 triples sent to a single consumer" in {
      val consumers = Set[Actor]()
      val consumedStatements = ListBuffer[Statement[SesameStatement]]()
      val processor = SesameRDFStreamProcessor(
        () => FileUtils.openResourceFile(Dataset),
        (s: SesameStatement) => consumers.foreach((c: Actor) => c ! Statement(s)), // send matched triple to all consumers
        List(sesameMatcher))
      val producer0 = processor.producer
      val producer1 = processor.producer
      consumers += processor.consumer((s: Statement[SesameStatement]) => consumedStatements += s)
      producer0 ! Start
      producer1 ! Start
      Thread.sleep(100)
      assert(consumedStatements.size == 32)
    }
  
  val mapSetMatcher = new MapSetMatcher[SesameStatement, URI, Value](
    (s: SesameStatement) => s.getSubject().toString().equals(JosefSpielerURI), // match condition
    (s: SesameStatement) => Some(s.getPredicate(),s.getObject()), // element to store in this Matcher's list
    (s: SesameStatement) => Some(Set(s))) // triple to store, the place for transformations
  
  "A MapSetMatcher" should "handle a Sesame Statement object correctly" in {
    SesameRDFStreamProcessor(
        () => FileUtils.openResourceFile(Dataset),
        (s: SesameStatement) => None,
        List(mapSetMatcher)).trigger
    assert(mapSetMatcher.map.size==16)
  }
  
  lazy val persistentSesameMapSetMatcher = new PersistentMapSetMatcher[SesameStatement, URI, Value](
    "jdbc:h2:mem:",
    (s: SesameStatement) => s.getSubject().stringValue().equals(JosefSpielerURI), // match condition
    (s: SesameStatement) => Some(s.getPredicate(),s.getObject()), // element to store in this Matcher's list
    (s: SesameStatement) => Some(Set(s))) // triple to store, the place for transformations
  
  "A PersistentMapSetMatcher" should "handle a Sesame Statement object correctly" in {
    SesameRDFStreamProcessor(
        () => FileUtils.openResourceFile(Dataset),
        (s: SesameStatement) => None,
        List(persistentSesameMapSetMatcher)).trigger
    assert(persistentSesameMapSetMatcher.map.size==16)
  }
  
  lazy val persistentSesameNestedMapSetMatcher = new PersistentNestedMapSetMatcher[SesameStatement, URI, Value, Resource](
    "jdbc:h2:mem:",
    (s: SesameStatement) => true, // match condition
    (s: SesameStatement) => Some((s.getPredicate(),s.getObject(),s.getSubject())), // element to store in this Matcher's list
    (s: SesameStatement) => Some(Set(s))) // triple to store, the place for transformations
  
  "A PersistentNestedMapSetMatcher" should "handle a Sesame Statement object correctly" in {
    SesameRDFStreamProcessor(
        () => FileUtils.openResourceFile(Dataset),
        (s: SesameStatement) => None,
        List(persistentSesameNestedMapSetMatcher)).trigger
    assert(persistentSesameNestedMapSetMatcher.map.size==19)
  }
}
