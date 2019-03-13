package graphdb

import java.util.UUID

import graphdb.models.FieldType
import graphdb.models.GraphDbDef.{Constraint, Node}

final case class CreateType(name: String, attributes: Map[String, FieldType])
//final case class GetType(name: String)
//final case class GetTypeReply(name: String, path: String, attributes: Map[String, FieldType])

final case class AddAttToType(typeName: String, attrName: String, attrType: FieldType)
final case class DeleteAttFromType(typeName: String, attrName: String)

final case class TypeCreationSuccess(message: String)
final case class RelationCreationSuccess(name: String, rid: Int)
final case class ObjectCreationSuccess(name: String, id: UUID)
final case class OperationSuccess(message: String)
final case class OperationFailure(reason: String)

final case class AddNode(typeName: String, attributes: Map[String, Any])

// This message is also for adding new attribute to a node.
final case class UpdateAttOnNode(nodeId: UUID, attributes: Map[String, Any])
final case class DeleteAttFromNode(nodeId: UUID, attributes: Seq[String])

/**
  * Define a relation.
  * @param relationName
  * @param ownerTypeName
  * @param targetTypes  Seq of type names that are the target of this relation.
  */
final case class CreateRelation(relationName: String, ownerTypeName: String, targetTypes: Seq[String])

/**
  * Add a directional relation from an node to one or more nodes.
  * @param ownerNode The source of the directional relation.
  * @param relationId
  * @param targetNodes The target of the
  */
final case class LinkMsg(ownerNodeId: UUID, relationId: Int, targetNodes: Seq[UUID])
final case class UnlinkByLinkId(linkId: UUID)

final case object Shutdown

// EVENT

/**
  *
  * @param id
  * @param RName Name of the Relation
  * @param ownerType Which type can own the Relation. To own a relation means to be the source of
  *                  the directional link.
  * @param targetList A Seq of the target type name
  */
final case class RTypeCreated(id: Int, RName: String, ownerType: String, targetList: Seq[String])

/**
  *
  * @param id
  * @param typeName
  * @param attributes A Map from attr name to attr value.
  */
final case class NodeCreated(id: UUID, typeName: String, attributes: Map[String, Any])

/**
  *
  * @param id Id of this Link
  * @param relationId
  * @param nodeName
  * @param targetList
  */
final case class LinkAdded(id: UUID, relationId: Int, ownerNodeId: UUID, targetNodes: Seq[UUID])

// Query
final case class Query(constraint: Constraint)
final case class QueryResult(nodes: Seq[Node])