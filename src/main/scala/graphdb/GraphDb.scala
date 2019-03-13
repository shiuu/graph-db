package graphdb

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor
//import akka.routing.FromConfig
//import com.typesafe.config.ConfigFactory
import graphdb.GraphDb.GraphDbStore.Shutdown
import graphdb.models._
import graphdb.models.GraphDbDef._

object GraphDb {
  object GraphDbStore {
    case class CreateType(name: String, attributes: Map[String, FieldType])
    case class GetType(name: String)
    case class GetTypeReply(name: String, path: String, attributes: Map[String, FieldType])

    case class AddAttToType(typeName: String, attrName: String, attrType: FieldType)
    case class DeleteAttFromType(typeName: String, attrName: String)

    case class TypeCreationSuccess(message: String)
    case class RelationCreationSuccess(name: String, rid: Int)
    case class ObjectCreationSuccess(name: String, id: UUID)
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
      * Add a directional relation from an node to one or more nodes.
      * @param ownerNode The source of the directional relation.
      * @param relationId
      * @param targetNodes The target of the
      */
    case class LinkMsg(ownerNodeId: UUID, relationId: Int, targetNodes: Seq[UUID])
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
//    var type2NodesMap: Map[String, Set[Node]] = Map()

    override def persistenceId: String = "graph-db"

    override def receiveCommand: Receive = {
      case typeCreation @ CreateType(typeName, attributes) =>
        typeMap.get(typeName) match {
          case Some(_) =>
            sender() ! OperationFailure(s"Cannot create type $typeName. Type $typeName already exists!")
          case None =>
            persist(typeCreation){ _ =>
              typeMap += (typeName -> attributes)
              sender() ! OperationSuccess(s"Type $typeName successfully created")
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
                  sender() ! OperationSuccess(s"Attribute $attrName successfully added")
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
                  sender() ! OperationSuccess(s"Type $typeName successfully deleted")
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

        persist(RTypeCreated(latestRid, relationName, ownerTypeName, targetTypes)){ e =>
          relationMap += (latestRid -> Relation(latestRid, relationName, ownerTypeName, targetTypes))
          sender() ! RelationCreationSuccess(s"Relation $relationName successfully created", latestRid)
          latestRid += 1;
          log.info(s"Persisted $e")
        }
      case AddNode(typeName, attributes) =>
        typeMap.get(typeName) match {
          case Some(_) =>
            // TODO: validate attributes
            val id = UUID.randomUUID()
            persist(NodeCreated(id, typeName, attributes)){ _ =>
              val nodeToAdd = Node(id, typeName, attributes)
              nodeMap += id -> nodeToAdd
//              val nodesOfType = type2NodesMap.getOrElse(typeName, Set.empty[Node])
//              type2NodesMap += typeName -> (nodesOfType + nodeToAdd)

              sender() ! ObjectCreationSuccess(s"Node successfully created", id)
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
              sender() ! OperationSuccess(s"Attribute(s) successfully updated")
              log.info(s"Persisted UpdateAttOnNode $newNode")
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
              sender() ! OperationSuccess(s"Attribute(s) successfully deleted")
              log.info(s"Persisted DeleteAttFromNode $newNode")
            }
          case None =>
            sender() ! OperationFailure(s"Invalid node id $nodeId !")
        }
      case LinkMsg(ownerNodeId, relationId, targetNodes) =>
        nodeMap.get(ownerNodeId) match {
          case Some(node) =>
            // TODO : need to validate relationId and targetNodes
            val id = UUID.randomUUID()
            persist(LinkAdded(id, relationId, ownerNodeId, targetNodes)) { _ =>
              val link = Link(id, relationId, ownerNodeId, targetNodes)
              val newNode = node.copy(linksOwned = node.linksOwned + link)
              nodeMap += ownerNodeId -> newNode

              for {tarNodeId <- targetNodes
                tarNode <- nodeMap.get(tarNodeId)
              } nodeMap += tarNodeId -> tarNode.copy(linksToThis = tarNode.linksToThis + link)

              sender() ! ObjectCreationSuccess(s"Link successfully created", id)

              log.info(s"Persisted LinkMsg $newNode")
            }
          case None =>
            sender() ! OperationFailure(s"Invalid node id $ownerNodeId !")
        }
      case q @ Query(constraint @ Constraint(typeName, attrMap)) =>
        val res = query(constraint)
        log.info(s"constraint: $constraint, query result: $res")
        sender() ! QueryResult(res)

//        type2NodesMap.get(typeName) match {
//          case Some(nodes) =>
//            val res = query(nodes, attrMap)
//            log.info(s"constraint: $constraint, query result: $res")
//            sender() ! QueryResult(res)
//          case None => sender() ! QueryResult(Seq.empty[Node])
//        }

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
        val newNode = Node(id, typeName, attributes)
        nodeMap += id -> newNode
//        val nodesOfType = type2NodesMap.getOrElse(typeName, Set.empty[Node])
//        type2NodesMap += typeName -> (nodesOfType + newNode)
        log.info(s"Recoverd NodeCreated $typeMap\n\tnodes: $nodeMap")
      case UpdateAttOnNode(nodeId, attributes) =>
        val node = nodeMap.getOrElse(nodeId, Node())
        val newAttributes = node.fields ++ attributes
        val newNode = node.copy(fields = newAttributes)
        nodeMap += (newNode.id -> newNode)
        log.info(s"Recoverd UpdateAttOnNode $newNode")
      case DeleteAttFromNode(nodeId, attributes) =>
        val node = nodeMap.getOrElse(nodeId, Node())
        val newAttributes = node.fields -- attributes
        val newNode = node.copy(fields = newAttributes)
        nodeMap += (newNode.id -> newNode)
        log.info(s"Recoverd DeleteAttFromNode $newNode")
      case LinkAdded(linkId, rid, ownerId, targetNodes) =>
        val node = nodeMap.getOrElse(ownerId, Node())
        val link = Link(linkId, rid, ownerId, targetNodes)
        val newNode = node.copy(linksOwned = node.linksOwned + link)
        nodeMap += ownerId -> newNode

        for {tarNodeId <- targetNodes
             tarNode <- nodeMap.get(tarNodeId)
        } nodeMap += tarNodeId -> tarNode.copy(linksToThis = tarNode.linksToThis + link)

        log.info(s"Recoverd LinkAdded $newNode")
    }
    // TODO handle persist failure/rejection

    private def query(constraint: Constraint): Seq[Node] = {
      val nodes = getNodesOfType(constraint.typeName)
      if(nodes.size == 0) Seq.empty[Node]
      else {
        query(nodes, constraint.attrMap)
      }
//      type2NodesMap.get(constraint.typeName) match {
//        case Some(nodes) =>
//          query(nodes, constraint.attrMap)
//        case None => Seq.empty[Node]
//      }
    }

    private def query(nodes: Set[Node], attrMap: Map[String, Any]): Seq[Node] = {
      var nodes2 = Set() ++ nodes

      attrMap.get("_Type") match {
        case Some(typeName) =>
          nodes2 = nodes2.filter(n => n.typeName == typeName)
        case None =>
      }
      attrMap.get("_OnLinkSrc") match {
        case Some(RConstraint(rid, None, Seq())) =>
          nodes2 = nodes2.filter(n =>
            n.linksOwned.exists(link => link.rid == rid)
          )
        case Some(RConstraint(rid, Some(ownerId), Seq())) =>
          nodes2 = nodes2.filter(n =>
            n.id == ownerId &&
            n.linksOwned.exists(link => link.rid == rid)
          )
        case Some(RConstraint(rid, None, tar @ Seq(_))) =>
          nodes2 = nodes2.filter(n =>
            n.linksOwned.exists(link => link.rid == rid && link.targetNodes == tar)
          )
        case _ =>
      }
      attrMap.get("_OnLinkTarget") match {
        case Some(RConstraint(rid, None, Seq())) =>
          val nodesToCheck = nodes2
          nodes2 = Set.empty[Node]
          nodesToCheck.foreach(n =>
            if(n.linksToThis.exists(link => link.rid == rid)){
              nodes2 += n
            }
          )
        case Some(RConstraint(rid, Some(ownerId), Seq())) =>
          val nodesToCheck = nodes2
          nodes2 = Set.empty[Node]
          nodesToCheck.foreach(n =>
            if(n.linksToThis.exists(link => link.rid == rid && link.ownerId == ownerId)){
              nodes2 += n
            }
          )
        case Some(RConstraint(rid, None, tar @ Seq(_))) =>
//          nodes2 = nodes.filter(n =>
//            n.linksOwned.exists(link => link.rid == rid && link.targetNodes == tar)
//          )
        case _ =>
      }


      for(entry <- attrMap) entry match {
        case (attrName, c @ Constraint(_, _)) =>
          val consNodes = query(c)
          val currentRes = nodes2
          nodes2 = Set.empty[Node]
          for(n <- consNodes) {
            nodes2 ++= query(currentRes, Map[String, Any](attrName -> n))
          }
        case (attrName, attrValue) =>
          nodes2 = nodes2.filter(n => n.fields.getOrElse(attrName, null) == attrValue)
      }

      nodes2.toSeq
    }

    private def getNodesOfType(typeName: String): Set[Node] = {
      nodeMap.values.filter(n => n.typeName == typeName).toSet
    }
  }

  /*
  object GraphDatabase extends App {
    class GraphDbWorker extends Actor {
      override def receive: Receive = ???
    }

    val system = ActorSystem("graphDb", ConfigFactory.load().getConfig("nigelTest"))
    val dbMaster = system.actorOf(FromConfig.props(Props[GraphDbWorker]), "workerRouter")
  }*/
//  def main(args: Array[String]){
//    import graphdb.GraphDb.GraphDbStore._
//
//    val system = ActorSystem("dbSys")
//    val db = system.actorOf(Props[GraphDbStore], "db")
//    db ! CreateType("Employee", Map("Name" -> FieldType.Str, "Age" -> FieldType.Number))
//    db ! CreateType("Business", Map())
//    db ! AddAttToType("Business", "Location", FieldType.Str)
//
//    db ! AddNode("Employee", Map("Name" -> "Alice", "Age" -> 38))
//    db ! AddNode("Employee", Map("Name" -> "Bob", "Age" -> 48))
//
//    db ! CreateRelation("FriendOf", "Employee", List("Employee"))

//    db ! LinkMsg()
//  }

}
