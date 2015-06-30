# rdfp
RDF stream processing framework.

Works with Sesame and Jena. Supports on-the-fly lucene indexing.
RDFStreamProcessingTest.scala contains several test cases which may serve as
examples on how to use.
The framework has been used successfully to process datasets containing more than
100 billion triples on datasets of the german national library.
It can process RDF streams in parallel since the Akka actors model has been applied.

## Installation
Clone the repository and run sbt in the project root folder.
Enter 'test' to run the tests or 'compile' to compile the project.

## Getting Started

The basic building blocks of a RDF stream processor in rdfp are matchers, sources
and sinks. While sources are mandatory, sinks are optional.
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
    (t: Triple) => t.getSubject().hasURI(JosefSpielerURI), // match condition
    (t: Triple) => Some(t.getObject()), // element to store in this Matcher's list
    (t: Triple) => Some(Set(t))) // triple to store, the place for transformations
```

Or with sesame:
```scala
val sesameMatcher = new SetMatcher(
    (s: SesameStatement) => s.getSubject().stringValue().equals(JosefSpielerURI), // match condition
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

## Parallelism

You can easily create multiple processors to run in parallel as the following
example illustrates with a Sesame RDF stream processor. The producer-consumer
pattern has been applied.
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
 - PersistentMap, Map[K, V] simple key-value SQL storage of arbitrary serializable objects. 
 - PersistentMapSet, Map[K, Set[V]] forms a mapping between an arbitrary serializable
 key object and a corresponding set containing arbitrary serializable objects
 - PersistentNestedMapSet, Map[K1, Map[K2, Set[V]]] mapping
```scala
// find all people
lazy val smwPersons = new PersistentSetMatcher[SesameStatement, Value](
	"jdbc:h2:" + path + "/tmp/rdfp.smw.persons",
	(s: SesameStatement) => RDFType.equals(s.getPredicate().toString()) && SMWPerson.equals(s.getObject().toString()), // match condition
	(s: SesameStatement) => Some(s.getSubject()), // element to store in this Matcher's list
	(s: SesameStatement) => None) // triple to store, the place for transformations
```

 ## Change Listeners
 
The source file RDFStreamProcessingHelper contains some utility classes. To
watch for changes on components of statments as triples pass along, use the
ComponentChangeHandler object.