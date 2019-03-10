package graphdb

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

object GraphDb {
  object GraphDatabase {
    case class CreateType(name: String)
    case class GetType(name: String)
    case class GetTypeReply(name: String, path: String, attributes: Map[String, FieldType])

    case class AddAttributeToType(typeName: String, name: String, attrType: FieldType)

    case class CreationSuccess(name: String, typePath: String)
    case class OperationSuccess(message: String)
    case class OperationFailure(reason: String)

    case class AddNode(name: String, typePath: String, attributes: Map[String, Any])
  }
  class GraphDatabase extends Actor with ActorLogging{
    import graphdb.GraphDb.GraphDatabase._
    import graphdb.GraphDb.DataType._

    override def receive: Receive = withTypes(Map())

    /**
      *
      * @param typeMap A map from type name to type actorRef.
      * @return
      */
    private def withTypes(typeMap: Map[String, ActorRef]): Receive = {
      case CreateType(name) =>
        typeMap.get(name) match {
          case Some(_) =>
            sender() ! OperationFailure(s"Cannot create type $name. Type $name already exists!")
          case None =>
            val newType = context.actorOf(Props[DataType], name)
            context.become(withTypes(typeMap + (name -> newType)))
            sender() ! CreationSuccess(name, newType.path.toString)
        }
      case AddAttributeToType(typeName, attrName, attrType) =>
        typeMap.get(typeName) match {
          case Some(typeRef) =>
            typeRef forward AddAttribute(attrName, attrType)
          case None =>
            sender() ! OperationFailure(s"Type $typeName doesn't exist!")
        }
      case AddNode(name, typeName, attributes) =>
        typeMap.get(typeName) match {
          case Some(typeRef) =>
            // TODO
          case None =>
            sender() ! OperationFailure(s"Invalid type name '$typeName'")
        }

      case message => log.info(message.toString)
    }
  }

  object DataType {
    case class AddAttribute(name: String, `type`: FieldType )
  }
  class DataType extends Actor {
    import graphdb.GraphDb.DataType._
    import graphdb.GraphDb.GraphDatabase._

    override def receive: Receive = withAttrs(Map())

    private def withAttrs(attrMap: Map[String, FieldType]): Receive = {
      case AddAttribute(attrName, attrType) =>
        attrMap.get(attrName) match {
          case Some(_) =>
            sender() !
              OperationFailure(s"Cannot add attribute '$attrName'. The attribute already exists in type ${self.path.name}!")
          case None =>
            context.become(withAttrs(attrMap + (attrName -> attrType)))
            sender() ! OperationSuccess(s"The attribute '${attrName}' successfully added in type ${self.path.name}")
        }
    }
  }
}
