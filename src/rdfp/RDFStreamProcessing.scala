package rdfp

import java.io.Reader
import org.openrdf.model.{ Statement => SesameStatement }
import org.openrdf.rio.{ RDFParser, ParserConfig, RioSetting }
import org.openrdf.rio.rdfxml.RDFXMLParser
import org.openrdf.rio.helpers.{ RDFHandlerBase, BasicParserSettings }
import org.apache.jena.atlas.lib.Sink
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.system.StreamRDFBase
import com.hp.hpl.jena.graph.Triple
import scala.actors.OutputChannel
import scala.actors._
import scala.actors.Actor._
import scala.collection.mutable.{ ListBuffer, Set, Map, HashMap, Stack }
import org.openrdf.model.Model
import org.openrdf.model.Value
import org.openrdf.model.Resource
import org.openrdf.model.BNode
import org.apache.lucene.document.Document
import org.apache.lucene.store.RAMDirectory
import org.openrdf.model.URI
import rdfp.ComponentChangeHandler._
import rdfp.BNodeHandler._
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.util.Version
import org.apache.lucene.search.similarities.DefaultSimilarity

trait RDFStreamProcessor[S] {
  val source: Any
  val sink: S => Unit
  val matchers: List[Matcher[S]]
  protected[rdfp] def handle(statement: S) =
    for (matcher <- matchers)
      if (matcher matches statement) {
        matcher handle statement
        matcher transform statement match {
          case Some(transformedStatements) => for (transformedStatement <- transformedStatements) sink(transformedStatement)
          case None => sink(statement)
        }
      }
  def trigger
  def producer(): Actor = actor {
    loop {
      receive {
        case Start => trigger
        case Stop => exit
        case _ => throw new RuntimeException("unknown message from " + sender)
      }
    }
  }
  def consumer(handle: Statement[S] => Unit): Actor = actor {
    loop {
      receive {
        case Stop => exit
        case s: Statement[S] => handle(s)
        case _ => throw new RuntimeException("unknown message from " + sender)
      }
    }
  }
}
case class Start
case class Stop
case class Statement[S](statement: S)
object SesameRDFStreamProcessor {
  implicit val bNStack = Stack[SesameStatement]()
  implicit val bNMapping = (s: SesameStatement, h: BNodeHandler[Value, Resource, Value, SesameStatement]) => Set(s)
  implicit def idxMapping(s: SesameStatement): Map[String, String] = Map(s.getPredicate().toString() -> s.getObject().stringValue())
  implicit val idxDirectory = new RAMDirectory()
  def apply(src: () => Reader,
    snk: SesameStatement => Unit,
    mtchrs: List[Matcher[SesameStatement]]) = new SesameRDFStreamProcessor(src, snk, mtchrs)
  class SesameRDFStreamProcessor(
    src: () => Reader,
    snk: SesameStatement => Unit,
    mtchrs: List[Matcher[SesameStatement]])(implicit bNStack: Stack[SesameStatement], bNMapping: (SesameStatement, BNodeHandler[Value, Resource, Value, SesameStatement]) => Set[SesameStatement])
    extends RDFHandlerBase with RDFStreamProcessor[SesameStatement] with ComponentChangeHandler[Value, Resource, URI, Value, SesameStatement] with BNodeHandler[Value, Resource, Value, SesameStatement] {
    override val source = src
    override val sink = snk
    override val matchers = mtchrs
    override val bNodeStack = bNStack
    override def bNodeMapping = (s: SesameStatement, p: BNodeHandler[Value, Resource, Value, SesameStatement]) => bNMapping(s, this)
    override def handleStatement(s: SesameStatement) = for (hs <- handleBNodesIn(s)) { handleChangesIn(hs); handle(hs) }
    override def trigger = trigger({
      val defaultParser = new RDFXMLParser()
      defaultParser.setRDFHandler(this)
      val parserConfig = defaultParser.getParserConfig()
      parserConfig.set(BasicParserSettings.FAIL_ON_UNKNOWN_DATATYPES.asInstanceOf[RioSetting[Boolean]], true)
      parserConfig.set(BasicParserSettings.VERIFY_DATATYPE_VALUES.asInstanceOf[RioSetting[Boolean]], false)
      defaultParser
    }, "org.apache.xerces.parsers.SAXParser")
    def trigger(parser: RDFParser, driver: String) = {
      System.setProperty("org.xml.sax.driver", driver)
      parser.parse(source(), "") // FIXME: set baseURI
    }
    override protected def subjectOf(s: SesameStatement) = s.getSubject()
    override protected def predicateOf(s: SesameStatement) = s.getPredicate()
    override protected def objectOf(s: SesameStatement) = s.getObject()
    override protected def isBNode(v: Value) = v.isInstanceOf[BNode]
  }
  class IndexingSesameRDFStreamProcessor(
    src: () => Reader,
    snk: SesameStatement => Unit,
    mtchrs: List[Matcher[SesameStatement]])(implicit bNStack: Stack[SesameStatement], bNMapping: (SesameStatement, BNodeHandler[Value, Resource, Value, SesameStatement]) => Set[SesameStatement], idxMapping: (SesameStatement) => Map[String, String], idxDirectory: Directory)
    extends { override val indexDirectory = idxDirectory; override val indexAnalyzer = new StandardAnalyzer(Version.LUCENE_CURRENT); override val indexSimilarity = new DefaultSimilarity() } with SesameRDFStreamProcessor(src, snk, mtchrs) with LuceneIndexer[Value, SesameStatement] with NewRootSubjectListener[Value, Resource, URI, Value, SesameStatement] with LuceneSearcher {
    this.componentChangeListeners += this
    this.levelChangeListeners += this
    override def onNewRootSubject(s: Resource) = newDocument(s.toString())
    override def indexMapping = idxMapping
//    override def closeIndexDirectory = indexDirectory.close()
    override def handleStatement(s: SesameStatement) = for (hs <- handleBNodesIn(s)) { handleChangesIn(hs); indexNodesIn(hs); handle(hs) }
//    override def finalize = { closeIndexWriter; closeIndexDirectory }
  }
}
class JenaRDFStreamProcessor(
  src: String,
  snk: Triple => Unit,
  mtchrs: List[Matcher[Triple]])
  extends StreamRDFBase with RDFStreamProcessor[Triple] {
  override val source = src
  override val sink = snk
  override val matchers = mtchrs
  override def triple(t: Triple) = handle(t)
  override def trigger = RDFDataMgr.parse(this, src)
}