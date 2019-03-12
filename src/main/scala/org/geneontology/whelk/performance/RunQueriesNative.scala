package org.geneontology.whelk.performance

import java.io.{File, PrintWriter}
import java.util.UUID
import java.util.concurrent.ForkJoinPool

import org.geneontology.whelk._
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.IRI

import scala.collection.parallel.ForkJoinTaskSupport

object RunQueriesNative {

  import RunQueries._

  def main(args: Array[String]): Unit = {
    val ontologyFile = new File(args(0))
    val parallelism = args(1).toInt
    val resultsFile = new File(args(2))
    val manager = OWLManager.createOWLOntologyManager()
    val ontology = manager.loadOntology(IRI.create(ontologyFile))
    val baseOntologySize = ontology.getLogicalAxiomCount
    val whelkOntology = Bridge.ontologyToAxioms(ontology)
    val queries = GenerateQueries.getQueriesFromOntologyExpressions(ontology).map(Bridge.convertExpression).flatten
    //val queries = GenerateQueries.getQueriesFromPropertyRestrictions(ontology)
    println(s"Ontology size: $baseOntologySize")
    println(s"Query number: ${queries.size}")
    val (reasoner, classificationTime) = time {
      Reasoner.assert(whelkOntology)
    }
    val memoryUsed = Runtime.getRuntime.totalMemory - Runtime.getRuntime.freeMemory
    println(s"Memory used after classification: $memoryUsed")
    println(s"Classification time: $classificationTime")
    val possiblyParallelQueriesToRun = if (parallelism > 1) {
      val parallelSeq = queries.par
      val forkJoinPool = new ForkJoinPool(parallelism)
      parallelSeq.tasksupport = new ForkJoinTaskSupport(forkJoinPool)
      parallelSeq
    } else queries
    val (results, queryTime) = time {
      possiblyParallelQueriesToRun.map { query =>
        val queryConcept = AtomicConcept(s"urn:uuid:${UUID.randomUUID.toString}")
        val axioms = Set(ConceptInclusion(queryConcept, query), ConceptInclusion(query, queryConcept))
        val updatedWhelk = Reasoner.assert(axioms, reasoner)
        val subclasses = updatedWhelk.closureSubsBySuperclass.getOrElse(queryConcept, Set.empty) + BuiltIn.Bottom
        val minusEquivs = subclasses.diff(updatedWhelk.closureSubsBySubclass.getOrElse(queryConcept, Set.empty))
        val trueSubclasses = minusEquivs.collect { case ac @ AtomicConcept(_) => ac }
        query -> trueSubclasses
      }
    }
    println(s"Memory used after querying: $memoryUsed")
    println(s"Query time: $queryTime")
    val writer = new PrintWriter(resultsFile, "utf-8")
    for {
      (query, subclasses) <- results.seq.sortBy(_._1.toString)
      sorted = subclasses.map(_.toString).toList.sorted.mkString("\t")
    } writer.println(s"$query\t$sorted")
    writer.close()
  }

}
