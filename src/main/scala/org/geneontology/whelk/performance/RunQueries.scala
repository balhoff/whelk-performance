package org.geneontology.whelk.performance

import java.io.{File, PrintWriter}
import java.util.concurrent.ForkJoinPool

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import org.geneontology.whelk.owlapi.WhelkOWLReasonerFactory
import org.phenoscape.scowl._
import org.semanticweb.elk.owlapi.ElkReasonerFactory
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.{IRI, OWLAxiom, OWLClass, OWLClassExpression}
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory

import scala.collection.JavaConverters._
import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.duration.Duration

object RunQueries {

  def main(args: Array[String]): Unit = {
    val ontologyFile = new File(args(0))
    val shouldNameQueries = args(1) match {
      case "true"  => true
      case "false" => false
    }
    val parallelism = args(2).toInt
    val reasonerName = args(3)
    val resultsFile = new File(args(4))
    val manager = OWLManager.createOWLOntologyManager()
    val ontology = manager.loadOntology(IRI.create(ontologyFile))
    val baseOntologySize = ontology.getLogicalAxiomCount
    val queries = GenerateQueries.getQueriesFromOntologyExpressions(ontology)
    println(s"Ontology size: $baseOntologySize")
    println(s"Query number: ${queries.size}")
    val (queriesToRun, axiomsToAdd) = if (shouldNameQueries) nameQueries(queries)
    else (queries, Set.empty[OWLAxiom])
    manager.addAxioms(ontology, axiomsToAdd.asJava)
    val (reasoner, classificationTime) = time {
      reasonerFactory(reasonerName).createReasoner(ontology)
    }
    println(s"Classification time: $classificationTime")

    val observableQueries = Observable.fromIterable(queriesToRun)
    val processed = observableQueries.mapParallelUnordered(parallelism = parallelism) { query =>
      Task {
        query -> reasoner.getSubClasses(query, false).getFlattened.asScala
      }
    }
//    val possiblyParallelQueriesToRun = if (parallelism > 1) {
//      val parallelSeq = queriesToRun.par
//      val forkJoinPool = new ForkJoinPool(parallelism)
//      parallelSeq.tasksupport = new ForkJoinTaskSupport(forkJoinPool)
//      parallelSeq
//    } else queriesToRun
    val (results, queryTime) = time {
      println("Running queries")
      processed.toListL.runSyncUnsafe(Duration.Inf)
//      possiblyParallelQueriesToRun.map { query =>
//        query -> reasoner.getSubClasses(query, false).getFlattened.asScala
//      }
    }
    reasoner.dispose()
    println(s"Query time: $queryTime")
    val writer = new PrintWriter(resultsFile, "utf-8")
    for {
      (query, subclasses) <- results.seq.sortBy(_._1.toString)
      sorted = subclasses.map(_.toString).toList.sorted.mkString("\t")
    } writer.println(s"$query\t$sorted")
    writer.close()
  }

  def nameQueries(queries: List[OWLClassExpression]): (List[OWLClass], Set[OWLAxiom]) = {
    val (names, axioms) = (for {
      (query, index) <- queries.zipWithIndex
      name = Class(s"http://example.org/testquery#$index")
    } yield name -> (name EquivalentTo query)).unzip
    (names, axioms.toSet)
  }

  def reasonerFactory(name: String): OWLReasonerFactory = name.toLowerCase match {
    case "elk"   => new ElkReasonerFactory()
    case "whelk" => new WhelkOWLReasonerFactory()
  }

  def time[T](operation: => T): (T, Long) = {
    val start = System.currentTimeMillis
    val completed = operation
    val stop = System.currentTimeMillis
    (completed, stop - start)
  }

}
