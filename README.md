# rdfp
RDF stream processing framework.

Works with Sesame and Jena. Supports on-the-fly lucene indexing.
RDFStreamProcessingTest.scala contains several test cases which may serve as
examples on how to use.
The framework has been used successfully to process datasets containing more than
100 billion triples on datasets of the german national library.

The basic building blocks of a RDF stream processor in rdfp are matchers, sources
and sinks. While sources are mandatory, sinks are optional.
To process RDF triples you have to define matchers, which define which triples
will be processed. A matcher consists of a match condition, definition of what
to store and how to transform it:

```scala
trait Matcher[S] {
  def matches: S => Boolean
  def handle: S => Option[_]
  def transform: S => Option[Set[S]]
}
```

The following examples match upon a certain URI, store the object of a matched
triple and apply no transformation.

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