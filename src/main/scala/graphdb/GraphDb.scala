package graphdb

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor
import graphdb.models.{FieldType, NodeCreated, RTypeCreated, TypeCreated}
import graphdb.models.GraphDbDef.{Node, Relation}

object GraphDb {
  object GraphDbStore {
    case class CreateType(name: String, attributes: Map[String, FieldType])
    case class GetType(name: String)
    case class GetTypeReply(name: String, path: String, attributes: Map[String, FieldType])

    case class AddAttToType(typeName: String, attrName: String, attrType: FieldType)
    case class DeleteAttFromType(typeName: String, attrName: String)

    case class CreationSuccess(name: String, typePath: String)
    case class OperationSuccess(message: String)
    case class OperationFailure(reason: String)

    case class AddNode(typeName: String, attributes: Map[String, Any])

    // This message is also for adding new attribute to a node.
    case class UpdateAttOnNode(nodeId: UUID, attributes: Map[String, Any])
    case class DeleteAttFromNode(nodeId: UUID, attributes: Seq[String])

    /**
      * Define a relation.
      * @param relationName
      * @param ownerTypeName
      * @param targetTypes  Seq of type names that are the target of this relation.
      */
    case class CreateRelation(relationName: String, ownerTypeName: String, targetTypes: Seq[String])

    /**
      * Add a directional relation to an node.
      * @param ownerNode The source of the directional relation.
      * @param relationId
      * @param targetNodes The target of the
      */
    case class LinkMsg(ownerNode: UUID, relationId: Int, targetNodes: Seq[UUID])
    case class UnlinkByLinkId(linkId: UUID)

    case object Shutdown
  }
  class GraphDbStore extends PersistentActor with ActorLogging {
    import graphdb.GraphDb.GraphDbStore._

    // schema
    var typeMap: Map[String, Map[String, FieldType]] = Map()
    var relationMap: Map[Int, Relation] = Map()

    // Latest relation Id
    var latestRid = 0

    var nodeMap: Map[UUID, Node] = Map()

    override def persistenceId: String = "graph-db"

    override def receiveCommand: Receive = {
      case typeCreation @ CreateType(typeName, attributes) =>
        typeMap.get(typeName) match {
          case Some(_) =>
            sender() ! OperationFailure(s"Cannot create type $typeName. Type $typeName already exists!")
          case None =>
            persist(typeCreation){ _ =>
              typeMap += (typeName -> attributes)
              log.info(s"Persisted $typeCreation")
            }
        }
      case addAttr @ AddAttToType(typeName, attrName, attrType) =>
        typeMap.get(typeName) match {
          case Some(attrMap) =>
            attrMap.get(attrName) match {
              case Some(_) =>
                sender() ! OperationFailure(s"Attribute $attrName already exists!")
              case None =>
                persist(addAttr){ _ =>
                  val newAttrMap = attrMap + (attrName -> attrType)
                  typeMap = typeMap + (typeName -> newAttrMap)
                  log.info(s"Persisted $addAttr")
                }
            }
          case None =>
            sender() ! OperationFailure(s"Type $typeName doesn't exist!")
        }
      case deleteAttr @ DeleteAttFromType(typeName, attrName) =>
        typeMap.get(typeName) match {
          case Some(attrMap) =>
            attrMap.get(attrName) match {
              case Some(_) =>
                persist(deleteAttr){ _ =>
                  val newAttrMap = attrMap - attrName
                  typeMap = typeMap + (typeName -> newAttrMap)

                  // remove the attribute from nodes of the type
                  for (node <- nodeMap.values if node.typeName == typeName) {
                    val newFields = node.fields - attrName
                    val newNode = node.copy(fields = newFields)
                    nodeMap += (newNode.id -> newNode)
                  }
                  log.info(s"Persisted $deleteAttr")
                }
              case None =>
                sender() ! OperationFailure(s"Attribute $attrName does not exists!")

            }
          case None =>
            sender() ! OperationFailure(s"Type $typeName doesn't exist!")
        }
      case CreateRelation(relationName, ownerTypeName, targetTypes) =>
        // TODO need to verify
        //  1. the relation does not exist yet
        //  2. targetTypes is not empty
//        val id = UUID.randomUUID
        persist(RTypeCreated(latestRid, relationName, ownerTypeName, targetTypes)){ _ =>
          relationMap += (latestRid -> Relation(latestRid, relationName, ownerTypeName, targetTypes))
          latestRid += 1;
        }
      case AddNode(typeName, attributes) =>
        typeMap.get(typeName) match {
          case Some(_) =>
            // TODO: verify attributes
            val id = UUID.randomUUID()
            persist(NodeCreated(id, typeName, attributes)){ _ =>
              nodeMap += id -> Node(id, typeName, attributes)
              log.info(s"Persisted $AddNode with id: $id")
            }
          case None =>
            sender() ! OperationFailure(s"Invalid type $typeName !")
        }
      case updateAtt @ UpdateAttOnNode(nodeId, attributes) =>
        nodeMap.get(nodeId) match {
          case Some(node) =>
            // TODO need to verify attributes
            persist(updateAtt) { _ =>
              val newAttributes = node.fields ++ attributes
              val newNode = node.copy(fields = newAttributes)
              nodeMap += (newNode.id -> newNode)
            }
          case None =>
            sender() ! OperationFailure(s"Invalid node id $nodeId !")
        }
      case delAtt @ DeleteAttFromNode(nodeId, attributes) =>
        nodeMap.get(nodeId) match {
          case Some(node) =>
            // TODO need to verify attributes
            persist(delAtt) { _ =>
              val newAttributes = node.fields -- attributes
              val newNode = node.copy(fields = newAttributes)
              nodeMap += (newNode.id -> newNode)
            }
          case None =>
            sender() ! OperationFailure(s"Invalid node id $nodeId !")
        }

      case Shutdown => context.stop(self)
      case message => log.info(message.toString)
    }

    /**
      * Handler that will be called on recovery
      */
    override def receiveRecover: Receive = {
      case CreateType(typeName, attributes) =>
        typeMap += (typeName -> attributes)
        log.info(s"Recoverd type $typeName, attributes: $attributes")
      case AddAttToType(typeName, attrName, attrType) =>
        val attrMap = typeMap.getOrElse(typeName, Map())
        val newAttrMap = attrMap + (attrName -> attrType)
        typeMap += (typeName -> newAttrMap)
        log.info(s"Recoverd AddAttToType $typeName, attributes: $newAttrMap")
      case DeleteAttFromType(typeName, attrName) =>
        val attrMap = typeMap.getOrElse(typeName, Map())
        val newAttrMap = attrMap - attrName
        typeMap = typeMap + (typeName -> newAttrMap)

        // remove the attribute from nodes of the type
        for (node <- nodeMap.values if node.typeName == typeName) {
          val newFields = node.fields - attrName
          val newNode = node.copy(fields = newFields)
          nodeMap += (newNode.id -> newNode)
        }
        log.info(s"Recoverd DeleteAttFromType $typeMap\n\tnodes: $nodeMap")
      case RTypeCreated(rid, rName, ownerType, targetList) =>
        relationMap += (rid -> Relation(rid, rName, ownerType, targetList))
        latestRid = rid + 1;
        log.info(s"Recoverd relation [$rid] $rName, ownerType: $ownerType")
      case NodeCreated(id, typeName, attributes) =>
        nodeMap += id -> Node(id, typeName, attributes)
        log.info(s"Recoverd NodeCreated $typeMap\n\tnodes: $nodeMap")
      case UpdateAttOnNode(nodeId, attributes) =>
        val node = nodeMap.getOrElse(nodeId, Node())
        val newAttributes = node.fields ++ attributes
        val newNode = node.copy(fields = newAttributes)
        nodeMap += (newNode.id -> newNode)
      case DeleteAttFromNode(nodeId, attributes) =>
        val node = nodeMap.getOrElse(nodeId, Node())
        val newAttributes = node.fields -- attributes
        val newNode = node.copy(fields = newAttributes)
        nodeMap += (newNode.id -> newNode)
    }

    // TODO handle persist failure/rejection
  }

//  def main(args: Array[String]){
//    import graphdb.GraphDb.GraphDatabase._
//
//    val system = ActorSystem("dbSys")
//    val db = system.actorOf(Props[GraphDatabase], "db")
//    db ! CreateType("Employee", Map("Name" -> FieldType.Str, "Age" -> FieldType.Number))
//    db ! CreateType("Business", Map())
//    db ! AddAttToType("Business", "Location", FieldType.Str)
//
//    db ! AddNode("Employee", Map("Name" -> "Alice", "Age" -> 38))
//
//    println()
//  }
}
