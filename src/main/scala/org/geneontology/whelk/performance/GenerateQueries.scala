package org.geneontology.whelk.performance

import java.io.{File, PrintWriter}

import org.phenoscape.scowl._
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model._
import org.semanticweb.owlapi.model.parameters.Imports

import scala.collection.JavaConverters._
import scala.util.Random

object GenerateQueries {

  def main(args: Array[String]): Unit = {
    val ontologyFile = new File(args(0))
    val queriesFile = new File(args(1))
    val maxQueries = args(2).toInt
    val manager = OWLManager.createOWLOntologyManager()
    val ontology = manager.loadOntology(IRI.create(ontologyFile))
    val classes = ontology.getClassesInSignature(Imports.INCLUDED).asScala.toList
    val properties = ontology.getObjectPropertiesInSignature(Imports.INCLUDED).asScala.toList
    val combos = for {
      property <- properties
      cls <- classes
    } yield s"${property.getIRI}\t${cls.getIRI}"
    val shuffledCombos = Random.shuffle(combos).take(maxQueries)
    val writer = new PrintWriter(queriesFile, "utf-8")
    shuffledCombos.foreach(writer.println)
    writer.close()
  }

  def parseQueries(lines: Iterator[String]): List[OWLClassExpression] =
    lines.map { line =>
      val items = line.split("\t", -1)
      val property = ObjectProperty(items(0).trim)
      val filler = Class(items(1).trim)
      property some filler
    }.toList


  def getQueriesFromOntologyExpressions(ontology: OWLOntology): List[OWLClassExpression] = (for {
    axiom <- ontology.getLogicalAxioms(Imports.INCLUDED).asScala
    expression <- axiom.getNestedClassExpressions.asScala
    if expression.isAnonymous
    if isOnlyNamedOrIntersectionOrSomeValuesFrom(expression)
  } yield expression).toSet.toList

  def getQueriesFromPropertyRestrictions(ontology: OWLOntology): List[OWLClassExpression] = {
    val properties = ontology.getObjectPropertiesInSignature(Imports.INCLUDED).asScala.toList
    val fillers = ontology.getClassesInSignature(Imports.INCLUDED).asScala.toList
    for {
      property <- properties
      filler <- fillers
      if !filler.isOWLThing
      if !filler.isOWLNothing
    } yield property some filler
  }

  def isOnlyNamedOrIntersectionOrSomeValuesFrom(expression: OWLClassExpression): Boolean = expression match {
    case _: OWLClass                => true
    case s: OWLObjectSomeValuesFrom => (s.getNestedClassExpressions.asScala - s).forall(isOnlyNamedOrIntersectionOrSomeValuesFrom)
    case i: OWLObjectIntersectionOf => (i.getNestedClassExpressions.asScala - i).forall(isOnlyNamedOrIntersectionOrSomeValuesFrom)
    case _                          => false
  }

}
