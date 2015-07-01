# rdfp
RDF stream processing framework written in Scala.

Works with Sesame and Jena and has a small footprint. Supports on-the-fly lucene indexing.
RDFStreamProcessingTest.scala contains several test cases which may serve as
examples on how to use.
The framework has been used successfully to process datasets containing more than
100 billion triples on datasets of the german national library.
It can process RDF streams in parallel since the Akka actors model has been applied.

## Installation
Clone the repository and run [sbt](http://www.scala-sbt.org/) in the project root folder.
Enter 'test' to run the tests, 'compile' to compile the project or 'doc' to
generate API documentation with scaladoc. Use the tests to verify all is running
correctly. 

## Getting Started

The basic building blocks of a RDF stream processor in rdfp are *matchers*, *sources*
and *sinks*. While sources are mandatory, sinks are optional.
To process RDF triples you have to define matchers, that define which triples
will be processed. A matcher consists of a match condition, definition of what
to store and how to transform it:

```scala
trait Matcher[S] {
  def matches: S => Boolean
  def handle: S => Option[_]
  def transform: S => Option[Set[S]]
}
```

The following examples match upon a certain URI on the subject, store the object
of a matched triple and apply no transformation.

With Jena:
```scala
val jenaMatcher = new SetMatcher(
    (t: Triple) => t.getSubject().hasURI(someURI), // match condition
    (t: Triple) => Some(t.getObject()), // element to store in this Matcher's list
    (t: Triple) => Some(Set(t))) // triple to store, the place for transformations
```


Or with sesame:
```scala
val sesameMatcher = new SetMatcher(
    (s: SesameStatement) => s.getSubject().stringValue().equals(someURI), // match condition
    (s: SesameStatement) => Some(s.getObject()), // element to store in this Matcher's list
    (s: SesameStatement) => Some(Set(s))) // triple to store, the place for transformations
```

To start processing the RDF source, set up a RDFStreamProcessor instance.
An RDFStreamProcessor consists of source, sink and a list of matchers:
```scala
trait RDFStreamProcessor[S] {
  val source: Any
  val sink: S => Unit
  val matchers: List[Matcher[S]]
  …
}
```

Implementations of RDFStreamProcessors exist for Jena and Sesame.
With Jena:
```scala
new JenaRDFStreamProcessor(
	"/path/to/dataset", // source
	(t: Triple) => None, // sink
	List(jenaMatcher)).trigger
```

With Sesame:
```scala
SesameRDFStreamProcessor(
        () => FileUtils.openResourceFile("/path/to/dataset"),
        (s: SesameStatement) => None,
        List(sesameMatcher)).trigger
```


## Parallel processing

You can easily create multiple processors to run in parallel as the following
example illustrates with a Sesame RDF stream processor. The producer-consumer
pattern has been applied.
The file ```src/test/scala/RDFStreamProcessingTest.scala``` contains some tests
which demonstrate how to use multiple processors.
```scala
val consumers = Set[ActorRef]()
val consumedStatements = ListBuffer[Statement[SesameStatement]]()
val processor = SesameRDFStreamProcessor(
	() => FileUtils.openResourceFile(Dataset),
	(s: SesameStatement) => consumers.foreach((c: ActorRef) => c ! Statement(s)), // send matched triple to all consumers
	List(sesameMatcher))
val producer0 = processor.producer
val producer1 = processor.producer
consumers += processor.consumer((s: Statement[SesameStatement]) => consumedStatements += s) // do something with the statement
producer0 ! Start
producer1 ! Start
```


## Persistence

Special classes exist to store matched elements in a SQL database by using the
H2 database. They are useful when dealing with RDF streams. These classes can be
used for general storage and are by modular design not dependent on the rest of
the rdfp code. Implemenatations are available for string keys and values.
 - ```PersistentMap```, Map[K, V] simple key-value SQL storage of arbitrary serializable objects. 
 - ```PersistentMapSet```, Map[K, Set[V]] forms a mapping between an arbitrary serializable
 key object and a corresponding set containing arbitrary serializable objects
 - ```PersistentNestedMapSet```, Map[K1, Map[K2, Set[V]]] mapping
```scala
// find all people
lazy val smwPersons = new PersistentSetMatcher[SesameStatement, Value](
	"jdbc:h2:" + path + "/tmp/rdfp.smw.persons",
	(s: SesameStatement) => RDFType.equals(s.getPredicate().toString()) && PersonURI.equals(s.getObject().toString()), // match condition
	(s: SesameStatement) => Some(s.getSubject()), // element to store in this Matcher's list
	(s: SesameStatement) => None) // triple to store, the place for transformations
```


## Change Listeners
 
The source file RDFStreamProcessingHelper contains some utility classes. To
watch for changes on components of statments as triples pass along, use the
```ComponentChangeHandler``` object.

```scala
ComponentChangeHandler
```


## Dealing with blank nodes

The ```SesameRDFStreamProcessor``` implements the ```BNodeHandler``` trait which
enables to keep track of blank nodes. For example it allows to keep track of the
root node of the blank node subgraph. Arbitrary transformations of the subgraphs
are possible.
The following example will *flatten* a subgraph containing blank nodes:

```scala
implicit val bNMapping = (s: SesameStatement, h: BNodeHandler[Value, Resource, Value, SesameStatement]) => {
      if (s.getSubject().isInstanceOf[BNode])
        Set(factory.createStatement(h.root.getOrElse(s.getSubject()), s.getPredicate(), s.getObject()))
      else
        Set[SesameStatement]()
    }
```

This blank node mapping will have the following effect:
```
<SomeSubject> <somePredicate> <b_node0>
<b_node0> <someOtherPredicate> <someObject>
```
Will yield two triples:
```
<SomeSubject> <somePredicate> <someObject>
<SomeSubject> <someOtherPredicate> <someObject>
```
Where SomeSubject is the *root node*.


## Lucene indexing

A ```IndexingSesameRDFStreamProcessor``` will by default use a ```StandardAnalyzer```,
and ```DefaultSimilarity```. It uses a ```ComponentChangeHandler``` and has a
special method ```onNewRootSubject``` which is called whenever the subject in
the RDF stream changes. Subgraphs containing blank nodes are automatically handled
(by *flattening* the subgraph - see previous section)
The code snippet below processes an RDF stream and creates an index with Lucene
on-the-fly. By default, a new subject will be mapped to a document in the Lucene
index. This can be changed by overriding the ```onNewRootSubject``` method.

```scala
implicit val idxDirectory = FSDirectory.open(new File("data/lucene.idx"))
val proc = new IndexingSesameRDFStreamProcessor(
	datasetReader, // input stream of statements
	(s: SesameStatement) => None, // element to store in the matcher
	List(new DefaultMatcher())) // process all statements
proc.trigger
proc.closeIndexWriter
proc.closeIndexDirectory
```

To search an index, standard Lucence queries can be used as in the example below:
```scala
val dnbIndex = LuceneSearcher(FSDirectory.open(new File("data/lucene.idx")))
dnbIndex.searchByParsedQuery(fac.createURI(gndo + "preferredNameForThePerson").toString(), "some label", 10)
```