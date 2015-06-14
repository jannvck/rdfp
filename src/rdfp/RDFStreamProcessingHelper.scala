package rdfp

import scala.collection.mutable.{ Stack, Map, Set, ListBuffer }
import scala.collection.JavaConversions._
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.util.Version
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.document.TextField
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.similarities.DefaultSimilarity
import org.apache.lucene.index.Term
import org.apache.lucene.store.Directory
import org.apache.lucene.index.IndexableField
import org.apache.lucene.search.Query
import ComponentChangeHandler._
import BNodeHandler._
import org.apache.lucene.store.LockObtainFailedException
import org.apache.lucene.store.RAMDirectory
import org.apache.lucene.store.FSDirectory
import java.io.File

object ComponentChangeHandler {
  abstract class ComponentChangeEvent[Component, Subject <: Component, Predicate <: Component, Object <: Component]
  case class SubjectChange[Component, Subject <: Component, Predicate <: Component, Object <: Component](current: Subject, last: Option[Subject]) extends ComponentChangeEvent[Component, Subject, Predicate, Object]
  case class PredicateChange[Component, Subject <: Component, Predicate <: Component, Object <: Component](current: Predicate, last: Option[Predicate]) extends ComponentChangeEvent[Component, Subject, Predicate, Object]
  case class ObjectChange[Component, Subject <: Component, Predicate <: Component, Object <: Component](current: Object, last: Option[Object]) extends ComponentChangeEvent[Component, Subject, Predicate, Object]
  trait ComponentChangeListener[Component, Subject <: Component, Predicate <: Component, Object <: Component] {
    def onComponentChange(e: ComponentChangeEvent[Component, Subject, Predicate, Object]): Unit
  }
  trait ComponentChangeHandler[Component, Subject <: Component, Predicate <: Component, Object <: Component, Statement] {
    private var lastSubject: Option[Subject] = None
    private var lastPredicate: Option[Predicate] = None
    private var lastObject: Option[Object] = None
    val componentChangeListeners = ListBuffer[ComponentChangeListener[Component, Subject, Predicate, Object]]()
    protected def handleChangesIn(s: Statement) = {
      def informOf(e: ComponentChangeEvent[Component, Subject, Predicate, Object]) = {
        componentChangeListeners.foreach(l => l.onComponentChange(e))
      }
      lastSubject match {
        case Some(lastSub) if !lastSub.equals(subjectOf(s)) =>
          informOf(SubjectChange(subjectOf(s), Some(lastSub)))
          lastSubject = Some(subjectOf(s))
        case None =>
          informOf(SubjectChange(subjectOf(s), None))
          lastSubject = Some(subjectOf(s))
        case _ =>
          lastSubject = Some(subjectOf(s))
      }
      lastPredicate match {
        case Some(lastPred) if !lastPred.equals(predicateOf(s)) =>
          informOf(PredicateChange(predicateOf(s), Some(lastPred)))
          lastPredicate = Some(predicateOf(s))
        case None =>
          informOf(PredicateChange(predicateOf(s), None))
          lastPredicate = Some(predicateOf(s))
        case _ =>
          lastPredicate = Some(predicateOf(s))
      }
      lastObject match {
        case Some(lastObj) if !lastObj.equals(objectOf(s)) =>
          informOf(ObjectChange(objectOf(s), Some(lastObj)))
          lastObject = Some(objectOf(s))
        case None =>
          informOf(ObjectChange(objectOf(s), None))
          lastObject = Some(objectOf(s))
        case _ =>
          lastObject = Some(objectOf(s))
      }
    }
    protected def subjectOf(s: Statement): Subject
    protected def predicateOf(s: Statement): Predicate
    protected def objectOf(s: Statement): Object
  }
}
object BNodeHandler {
  abstract class LevelChangeEvent
  case class LevelUp extends LevelChangeEvent
  case class LevelsDown(levels: Int) extends LevelChangeEvent
  case class Clear extends LevelChangeEvent
  trait LevelChangeListener {
    def onLevelChange(e: LevelChangeEvent): Unit
  }
}
trait BNodeHandler[Component, Subject <: Component, Object <: Component, Statement] {
    implicit def convert(c: Component) = new IsBNodeWrapper(c)
    protected class IsBNodeWrapper(c: Component) {
      def isBNode = BNodeHandler.this.isBNode(c)
      def parentIndex = bNodeStack.indexWhere(s => subjectOf(s).equals(c))
    }
    protected val bNodeStack: Stack[Statement]
    protected def bNodeMapping: (Statement, BNodeHandler[Component, Subject, Object, Statement]) => Set[Statement]
    val levelChangeListeners = ListBuffer[LevelChangeListener]()
    protected def handleBNodesIn(s: Statement): Set[Statement] = {
      //    Console.println("BNodeHandler.handleBNodesIn("+s+")")
      def popUntil(s: Subject) = (0 to s.parentIndex).foreach(i => bNodeStack.pop)
      (subjectOf(s), objectOf(s)) match {
        case (subj, obj) if !subj.isBNode && obj.isBNode =>
          val result = bNodeMapping(s, this)
          bNodeStack.push(s)
          levelChangeListeners.foreach(l => l.onLevelChange(LevelUp()))
          result
        case (subj, obj) if subj.isBNode && obj.isBNode =>
          val preLevel = bNodeStack.size
          popUntil(subj)
          val diff = preLevel - bNodeStack.size
          if (diff > 0)
            levelChangeListeners.foreach(l => l.onLevelChange(LevelsDown(diff)))
          val result = bNodeMapping(s, this)
          bNodeStack.push(s)
          levelChangeListeners.foreach(l => l.onLevelChange(LevelUp()))
          result
        case (subj, obj) if subj.isBNode && !obj.isBNode =>
          val preLevel = bNodeStack.size
          popUntil(subj)
          val diff = preLevel - bNodeStack.size
          if (diff > 0)
            levelChangeListeners.foreach(l => l.onLevelChange(LevelsDown(diff)))
          bNodeMapping(s, this)
        case (subj, obj) if !(subj isBNode) && !(obj isBNode) && !bNodeStack.isEmpty =>
          bNodeStack.clear
          levelChangeListeners.foreach(l => l.onLevelChange(Clear()))
          Set(s)
        case _ => Set(s)
      }
    }
    def ancestors = bNodeStack
    def root: Option[Subject] = if (!bNodeStack.isEmpty) Some(subjectOf(bNodeStack.last)) else None
    protected def subjectOf(s: Statement): Subject
    protected def objectOf(s: Statement): Object
    protected def isBNode(c: Component): Boolean
  }
protected trait NewRootSubjectListener[Component, Subject <: Component, Predicate <: Component, Object <: Component, Statement] extends ComponentChangeListener[Component, Subject, Predicate, Object] with LevelChangeListener {
  private var clear = true
  private var fired = false
  def onNewRootSubject(s: Subject): Unit
  def onLevelChange(e: LevelChangeEvent): Unit = {
    e match {
      case Clear() => clear = true
      case LevelUp() => clear = true
      case e: LevelChangeEvent => clear = false
    }
  }
  def onComponentChange(e: ComponentChangeEvent[Component, Subject, Predicate, Object]): Unit = {
    e match {
      case SubjectChange(current, last) if (clear && !fired) =>
        onNewRootSubject(current); fired = true
      case _ => fired = false
    }
  }
}
//object LuceneStringNodeIndexer {
//  protected class IndexSearcher[Component,Statement](index: Directory) extends { protected val indexDirectory = index } with LuceneStringNodeIndexer[Component,Statement] {
//    protected def indexMapping = (s: Statement) => Map[String,String]()
//  }
//  def forSearchIn[Component, Statement](index: Directory) = new IndexSearcher[Component,Statement](index)
//}
trait LuceneIndexer[Component, Statement] {
  protected val indexAnalyzer = new StandardAnalyzer(Version.LUCENE_CURRENT)
  protected val indexDirectory: Directory
  protected val indexSimilarity = new DefaultSimilarity()
  protected val indexWriterConfig = () => new IndexWriterConfig(Version.LUCENE_CURRENT, indexAnalyzer).
    setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND).
    setSimilarity(indexSimilarity)
  protected val indexWriter = new IndexWriter(indexDirectory, indexWriterConfig())
  protected def indexMapping: Statement => Map[String, String]
  private var currentDoc: Option[Document] = Some(new Document())
  protected def newDocument(key: String): Document = {
    if (currentDoc.isDefined)
      indexWriter.addDocument(currentDoc.get)
    currentDoc = Some(new Document())
    currentDoc.get.add(new Field("_key_", key, TextField.TYPE_STORED))
    currentDoc.get
  }
  protected def indexNodesIn(s: Statement): Unit = {
    val mappings = indexMapping(s)
    currentDoc match {
      case Some(currentDoc) =>
        mappings.foreach(mapping => currentDoc.add(new Field(mapping._1, mapping._2, TextField.TYPE_STORED)))
      case _ => throw new RuntimeException("No document to add fields to, call newDocument() at least once before indexing")
    }
  }
  def commit = indexWriter.commit()
//  def close = indexWriter.close()
  def closeIndexWriter = indexWriter.close()
  def closeIndexDirectory = indexDirectory.close()
//  override def finalize = { indexWriter.close(); indexDirectory.close() }
//  override def finalize = indexWriter.close()
  def escapeForQuery(s: String): String = {
    /*
     * http://lucene.apache.org/core/4_7_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#Escaping_Special_Characters
     * + - && || ! ( ) { } [ ] ^ " ~ * ? : \ /
     */
    s.replaceAllLiterally("\\", "\\\\").
      replaceAllLiterally("+", "\\+").
      replaceAllLiterally("-", "\\-").
      replaceAllLiterally("&&", "\\&&").
      replaceAllLiterally("||", "\\||").
      replaceAllLiterally("!", "\\!").
      replaceAllLiterally("(", "\\(").
      replaceAllLiterally(")", "\\)").
      replaceAllLiterally("{", "\\{").
      replaceAllLiterally("}", "\\}").
      replaceAllLiterally("]", "\\]").
      replaceAllLiterally("^", "\\^").
      replaceAllLiterally("\"", "\\\"").
      replaceAllLiterally("~", "\\~").
      replaceAllLiterally("*", "\\*").
      replaceAllLiterally("?", "\\?").
      replaceAllLiterally(":", "\\:").
      replaceAllLiterally("/", "\\/")
  }
}
object LuceneSearcher {
  class StandaloneLuceneSearcher(index: Directory) extends {
    override protected val indexDirectory = index
  } with LuceneSearcher
  def apply(index: Directory) = new StandaloneLuceneSearcher(index)
}
trait LuceneSearcher {
  protected val indexAnalyzer = new StandardAnalyzer(Version.LUCENE_CURRENT)
  protected val indexDirectory: Directory
  lazy protected val ireader = DirectoryReader.open(indexDirectory)
  lazy protected val isearcher = new IndexSearcher(ireader)
  protected val indexSimilarity = new DefaultSimilarity()
  def searchByParsedQuery(field: String, query: String, results: Int) =
    search(new QueryParser(Version.LUCENE_CURRENT, field, indexAnalyzer).parse(query), results)
  def search(query: Query, results: Int): List[Map[String, Set[String]]] = {
    Console.println("ireader.numDocs()=" + ireader.numDocs())
    isearcher.setSimilarity(indexSimilarity)
    Console.println("query: " + query)
    val hits = isearcher.search(query, null, results).scoreDocs
    val result = ListBuffer[Map[String, Set[String]]]()
    Console.println(hits.length + " hits")
    for (hit <- hits) {
      val hitDoc = isearcher.doc(hit.doc)
      Console.println(hit.score + "\t" + hitDoc)
      Console.println("\t" + hitDoc.getFields().size() + " fields:")
      for (field <- hitDoc.getFields())
        Console.println("\t\t" + field)
    }
    implicit class IteratorWrapper(itr: Iterator[IndexableField]) extends Iterator[(String, String)] {
      override def hasNext = itr.hasNext
      override def next = { val next = itr.next; (next.name(), next.stringValue()) }
    }
    for (hit <- hits) {
      result += {
        val map = Map[String, Set[String]]()
        for (field <- isearcher.doc(hit.doc).toIterator)
          map.getOrElseUpdate(field.name(), Set(field.stringValue())) += field.stringValue()
        map
      }
    }
//    ireader.close()
    result.toList
  }
  def closeIndexDirectoryReader = ireader.close()
//  override def finalize() = ireader.close()
}