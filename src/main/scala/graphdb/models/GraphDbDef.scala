package graphdb.models

import java.util.UUID

object GraphDbDef {

  case class Relation(id: Int, name: String, ownerType: String, targetTypes: Seq[String])

  /**
    * A link is a directional relation from one node to one or more nodes.
    * @param id Id of the link
    * @param rid Id of the relation
    * @param ownerId
    * @param targetNodes
    */
  case class Link(id: UUID, rid: Int, ownerId: UUID, targetNodes: Seq[UUID])

  /**
    * A node is a vertex in the graph, also an object this is stored in the graph db.
    *
    * @param id         Node Id.
    * @param typeName
    * @param fields     A map from field name to the value of the field.
    * @param linksOwned linksOwned contains all the directed edges that starts from this node.
    * @param linksToThis linksOwned contains all the edges that directs to this node.
    */
  case class Node(id: UUID = null,
                  typeName: String = "",
                  fields: Map[String, Any] = Map(),
                  linksOwned:     Set[Link] = Set(),
                  linksToThis:    Set[Link] = Set()
                 )

  /** The constraint in a query */
  abstract class Constraint

  /** The constraint on nodes.
    * @param typeName the type to query on
    * @param attrMap a map from attribute name to attribute value or constraint.
    *                "_Type", "_OnLinkSrc" and "_OnLinkTarget" are system reserved attributes
    *                which stand for name of the type, link that owned by a node (nodeId indicated by
    *                value of the entry) and link that points to the value
    */
  case class NodeConstraint(typeName: String, attrMap: Map[String, Any]) extends Constraint

  /** Relation constraint for query
    * @param rid Relation Id
    * @param ownerNodeId
    * @param targetNodes
    */
  case class RConstraint(rid: Int, ownerNodeId: Option[UUID], targetNodes: Seq[UUID]) extends Constraint

  /**
    * Higher order relation constraint
    * @param rid
    * @param ownerNodeId
    * @param cons
    */
  case class HORConstraint(rid: Int, ownerNodeId: Option[UUID], cons: Constraint)

  // TODO Need higher order node constraint
}
