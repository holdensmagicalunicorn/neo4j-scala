package org.neo4j.scala

import org.specs._
import org.specs.runner._

import org.neo4j.graphdb._
import org.neo4j.kernel.EmbeddedGraphDatabase


class Neo4jWrapperSpecTest extends JUnit4(Neo4jWrapperSpec)

object Neo4jWrapperSpec extends Specification with Neo4jWrapper with EmbeddedGraphDatabaseServiceProvider {

  def neo4jStoreDir = "/tmp/temp-neo-test"

  "NeoWrapper" should {
    shareVariables()

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run() {
        ds.gds.shutdown
      }
    })

    "create a new relationship in --> relType --> notation" in {
      withTx {
        implicit neo =>
          val start = createNode
          val end = createNode
          val relType = DynamicRelationshipType.withName("foo")
          start --> relType --> end
          start.getSingleRelationship(relType, Direction.OUTGOING).
            getOtherNode(start) must beEqualTo(end)
      }
    }

    "create a new relationship in --> \"relName\" --> notation" in {
      withTx {
        implicit neo =>
          val start = createNode
          val end = createNode
          start --> "foo" --> end
          start.getSingleRelationship(DynamicRelationshipType.withName("foo"), Direction.OUTGOING).
            getOtherNode(start) must beEqualTo(end)
      }
    }

    "create a new relationship in <-- relType <-- notation" in {
      withTx {
        implicit neo =>
          val start = createNode
          val end = createNode
          val relType = DynamicRelationshipType.withName("foo")
          end <-- relType <-- start
          start.getSingleRelationship(relType, Direction.OUTGOING).
            getOtherNode(start) must beEqualTo(end)
      }
    }

    "create a new relationship in <-- \"relName\" <-- notation" in {
      withTx {
        implicit neo =>
          val start = createNode
          val end = createNode
          end <-- "foo" <-- start
          start.getSingleRelationship(DynamicRelationshipType.withName("foo"), Direction.OUTGOING).
            getOtherNode(start) must beEqualTo(end)
      }
    }

    "allow relationships of the same direction to be chained" in {
      withTx {
        implicit neo =>
          val start = createNode
          val middle = createNode
          val end = createNode
          start --> "foo" --> middle --> "bar" --> end
          start.getSingleRelationship(DynamicRelationshipType.withName("foo"), Direction.OUTGOING).
            getOtherNode(start) must beEqualTo(middle)
          middle.getSingleRelationship(DynamicRelationshipType.withName("bar"), Direction.OUTGOING).
            getOtherNode(middle) must beEqualTo(end)
      }
    }

    "allow relationships of different directions to be chained" in {
      withTx {
        implicit neo =>
          val left = createNode
          val middle = createNode
          val right = createNode
          left --> "foo" --> middle <-- "bar" <-- right
          left.getSingleRelationship(DynamicRelationshipType.withName("foo"), Direction.OUTGOING).
            getOtherNode(left) must beEqualTo(middle)
          right.getSingleRelationship(DynamicRelationshipType.withName("bar"), Direction.OUTGOING).
            getOtherNode(right) must beEqualTo(middle)
      }
    }

    "ignore a relationshipBuilder with no end node" in {
      withTx {
        implicit neo =>
          val start = createNode
          start --> "foo"
          start.getRelationships.iterator.hasNext must beEqualTo(false)
      }
    }

    "read a property in a node in node('property') notation" in {
      withTx {
        implicit neo =>
          val start = createNode
          start.setProperty("foo", "bar")
          start("foo") must beEqualTo(Some("bar"))
          start("bar") must beEqualTo(None)
      }
    }

    "create a property in a node in node('property')=value notation" in {
      withTx {
        implicit neo =>
          val start = createNode
          start("foo") = "bar"
          start.getProperty("foo") must beEqualTo("bar")
      }
    }

    "read a property in a relationship in rel('property') notation" in {
      withTx {
        implicit neo =>
          val start = createNode
          val end = createNode
          val rel = start.createRelationshipTo(end, DynamicRelationshipType.withName("foo"))
          rel.setProperty("foo", "bar")
          rel("foo") must beEqualTo(Some("bar"))
          rel("bar") must beEqualTo(None)
      }
    }

    "create a property in a relationship in rel('property')=value notation" in {
      withTx {
        implicit neo =>
          val start = createNode
          val end = createNode
          val rel = start.createRelationshipTo(end, DynamicRelationshipType.withName("foo"))
          rel("foo") = "bar"
          rel.getProperty("foo") must beEqualTo("bar")
      }
    }

    "allow writing stop evaluators in a functional style" in {
      withTx {
        implicit neo =>
          val start = createNode
          val end = createNode
          val rel = start.createRelationshipTo(end, DynamicRelationshipType.withName("foo"))
          val traverser = start.traverse(Traverser.Order.BREADTH_FIRST, (tp: TraversalPosition) => false, ReturnableEvaluator.ALL_BUT_START_NODE, DynamicRelationshipType.withName("foo"), Direction.OUTGOING)
          traverser.iterator.hasNext must beEqualTo(true)
          traverser.iterator.next must beEqualTo(end)
      }
    }

    "allow writing returnable evaluators in a functional style" in {
      withTx {
        implicit neo =>
          val start = createNode
          val end = createNode
          val rel = start.createRelationshipTo(end, DynamicRelationshipType.withName("foo"))
          val traverser = start.traverse(Traverser.Order.BREADTH_FIRST, StopEvaluator.END_OF_GRAPH, (tp: TraversalPosition) => tp.notStartNode(), DynamicRelationshipType.withName("foo"), Direction.OUTGOING)
          traverser.iterator.hasNext must beEqualTo(true)
          traverser.iterator.next must beEqualTo(end)
      }
    }
  }
}
