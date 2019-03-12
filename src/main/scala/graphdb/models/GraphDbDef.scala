package graphdb.models

import java.util.UUID

object GraphDbDef {
//  type RID =

  case class Relation(id: Int, ownerType: String, name: String, targetTypes: Seq[String])

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
  case class Node(id: UUID, typeName: String, fields: Map[String, Any], links: Map[Int, Link] = Map())
}
