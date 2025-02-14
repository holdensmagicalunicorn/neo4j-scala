package org.neo4j.scala

import util.CaseClassDeserializer
import collection.JavaConversions._
import org.neo4j.graphdb.{Relationship, PropertyContainer, RelationshipType, Node}

/**
 * Extend your class with this trait to get really neat new notation for creating
 * new relationships. For example, ugly Java-esque code like:
 * <pre>
 * val knows = DynamicRelationshipType.withName("KNOWS")
 * start.createRelationshipTo(intermediary, knows)
 * intermediary.createRelationshipTo(end, knows)
 * </pre>
 *
 * can be replaced with a beautiful Scala one-liner:
 * <pre>start --> "KNOWS" --> intermediary --> "KNOWS" --> end</pre>
 *
 * Feel free to use this example to tell all your friends how awesome scala is :)
 */
trait Neo4jWrapper extends Neo4jWrapperUtil {

  def ds: DatabaseService

  val ClassPropertyName = "__CLASS__"

  /**
   * Execute instructions within a Neo4j transaction; rollback if exception is raised and
   * commit otherwise; and return the return value from the operation.
   */
  def withTx[T <: Any](operation: DatabaseService => T): T = {
    val tx = synchronized {
      ds.gds.beginTx
    }
    try {
      val ret = operation(ds)
      tx.success
      return ret
    } finally {
      tx.finish
    }
  }

  /**
   * creates a new Node from Database service
   */
  def createNode(implicit ds: DatabaseService): Node = ds.gds.createNode

  /**
   * convenience method to create and serialize
   */
  def createNode[T <: Product](cc: T)(implicit ds: DatabaseService): Node = serialize(cc, createNode)

  /**
   * serializes a given case class into a Node instance
   */
  def serialize[T <: Product](cc: T, node: Node): Node = {
    CaseClassDeserializer.serialize(cc).foreach {
      case (name, value) => node.setProperty(name, value)
    }
    node.setProperty(ClassPropertyName, cc.getClass.toString)
    node
  }

  /**
   * deserializes a given case class type from a given Node instance
   */
  def deSerialize[T <: Product](node: Node)(implicit m: ClassManifest[T]): T = {
    val cpn = node.getProperty(ClassPropertyName).asInstanceOf[String]
    val kv = for (k <- node.getPropertyKeys; v = node.getProperty(k)) yield (k -> v)
    val o = CaseClassDeserializer.deserialize[T](kv.toMap)(m)
    if (cpn != null) {
      if (!cpn.equalsIgnoreCase(o.getClass.toString))
        throw new IllegalArgumentException("given Case Class does not fit to serialized properties")
    }
    o
  }
}

/**
 * creates incoming and outgoing relationships
 */
private[scala] class NodeRelationshipMethods(node: Node, rel: Relationship = null) {
  def -->(relType: RelationshipType) = new OutgoingRelationshipBuilder(node, relType)

  def <--(relType: RelationshipType) = new IncomingRelationshipBuilder(node, relType)

  def < = rel
}

/**
 * Half-way through building an outgoing relationship
 */
private[scala] class OutgoingRelationshipBuilder(fromNode: Node, relType: RelationshipType) {
  def -->(toNode: Node) = {
    val rel = fromNode.createRelationshipTo(toNode, relType)
    new NodeRelationshipMethods(toNode, rel)
  }
}

/**
 * Half-way through building an incoming relationship
 */
private[scala] class IncomingRelationshipBuilder(toNode: Node, relType: RelationshipType) {
  def <--(fromNode: Node) = {
    val rel = fromNode.createRelationshipTo(toNode, relType)
    new NodeRelationshipMethods(fromNode, rel)
  }
}

/**
 * convenience for handling properties
 */
private[scala] class RichPropertyContainer(propertyContainer: PropertyContainer) {
  def apply(property: String): Option[Any] =
    propertyContainer.hasProperty(property) match {
      case true => Some(propertyContainer.getProperty(property))
      case _ => None
    }

  def update(property: String, value: Any): Unit = propertyContainer.setProperty(property, value)
}