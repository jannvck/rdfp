name := "rdfp"
 
version := "1.0"
 
scalaVersion := "2.11.7"

libraryDependencies += "org.apache.jena" % "apache-jena-libs" % "2.13.0"

libraryDependencies += "org.openrdf.sesame" % "sesame-queryalgebra-evaluation" % "2.8.3"

libraryDependencies += "org.openrdf.sesame" % "sesame-queryparser-sparql" % "2.8.3"

libraryDependencies += "org.openrdf.sesame" % "sesame-queryresultio-sparqljson" % "2.8.3"

libraryDependencies += "org.openrdf.sesame" % "sesame-rio-turtle" % "2.8.3"

libraryDependencies += "org.openrdf.sesame" % "sesame-rio-rdfxml" % "2.8.3"

libraryDependencies += "org.openrdf.sesame" % "sesame-sail-memory" % "2.8.3"

libraryDependencies += "org.openrdf.sesame" % "sesame-sail-nativerdf" % "2.8.3"

libraryDependencies += "org.openrdf.sesame" % "sesame-repository-sail" % "2.8.3"

libraryDependencies += "org.apache.lucene" % "lucene-core" % "5.2.1"

libraryDependencies += "org.apache.lucene" % "lucene-analyzers-common" % "5.2.1"

libraryDependencies += "org.apache.lucene" % "lucene-queryparser" % "5.2.1"

libraryDependencies += "com.j256.ormlite" % "ormlite-core" % "4.48"

libraryDependencies += "com.j256.ormlite" % "ormlite-jdbc" % "4.48"

libraryDependencies += "com.h2database" % "h2" % "1.4.187"

libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.11"
