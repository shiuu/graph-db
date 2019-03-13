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
    * @param id Id of the node.
    * @param fields A map from field name to the value of the field.
    * @param links links contains all the directed edges that starts from this node. It
    *              is a map from relation id to Link.
    */
  case class Node(id: UUID = null,
                  typeName: String = "",
                  fields: Map[String, Any] = Map.empty[String, Any],
                  //                  links: Map[Int, Link] = Map()
                  linksOwned:     Set[Link] = Set(),
                  linksToThis:    Set[Link] = Set()
                 )

  /**
    * Constraint for query
    * @param typeName Type to query on
    * @param attrMap Map from attribute name to attribute value or constraint.
    *                "_Type", "_OnLinkSrc" and "_OnLinkTarget" system reserved attributes
    *                which are name of the type, link that owned by a node (nodeId indicated by
    *                value of the entry) and link that points to the value
    */
  case class Constraint(typeName: String, attrMap: Map[String, Any])

  /**
    * Relation constraint for query
    * @param rid
    * @param ownerNodeId
    * @param targetNodes
    */
  case class RConstraint(rid: Int, ownerNodeId: Option[UUID], targetNodes: Seq[UUID])

  /**
    * Higher order relation constraint
    * @param rid
    * @param ownerNodeId
    * @param cons
    */
  case class HORConstraint(rid: Int, ownerNodeId: Option[UUID], cons: Constraint)
}
