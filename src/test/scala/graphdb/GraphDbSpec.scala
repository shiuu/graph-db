package graphdb

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import graphdb.GraphDb.GraphDbStore
import graphdb.GraphDb.GraphDbStore._
import graphdb.models.FieldType
import graphdb.models.GraphDbDef.{Constraint, RConstraint}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class GraphDbSpec extends TestKit(ActorSystem("GraphDbSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A GraphDb actor" should {
    val dbActor = system.actorOf(Props[GraphDbStore])

    "create Employee type" in {
      val msg = CreateType("Employee", Map("Name" -> FieldType.Str, "Age" -> FieldType.Number))
      dbActor ! msg

      expectMsgType[OperationSuccess]
    }
    "create Business with no attribute, then add an attribute" in {
      dbActor ! CreateType("Business", Map())
      expectMsgType[OperationSuccess]

      dbActor ! AddAttToType("Business", "Location", FieldType.Str)
      expectMsgType[OperationSuccess]
//    }
//    "Cannot create a type that already exist" in {
      dbActor ! CreateType("Business", Map())
      expectMsgType[OperationFailure]
//    }
//    "Add employees and verify" in {
      dbActor ! AddNode("Employee", Map("Name" -> "Alice", "Age" -> 38))
      val aliceMsg = expectMsgType[ObjectCreationSuccess]
      val aliceId = aliceMsg.id

      dbActor ! AddNode("Employee", Map("Name" -> "Bob", "Age" -> 48))
      val bobMsg = expectMsgType[ObjectCreationSuccess]

      dbActor ! AddNode("Employee", Map("Name" -> "Chen", "Age" -> 48))
      val chenMsg = expectMsgType[ObjectCreationSuccess]

      dbActor ! AddNode("Employee", Map("Name" -> "Dan", "Age" -> 48))
      val danMsg = expectMsgType[ObjectCreationSuccess]

      // Define Friend-Of relation
      dbActor ! CreateRelation("FriendOf", "Employee", List("Employee"))
      val friendship = expectMsgType[RelationCreationSuccess]

      // Befriend Bob to Alice
      dbActor ! LinkMsg(bobMsg.id, friendship.rid, List(aliceId))
      expectMsgType[ObjectCreationSuccess]

      val cons = Constraint("Employee",
        Map("_OnLink" -> RConstraint(friendship.rid, Some(bobMsg.id), Seq.empty[UUID])))

      dbActor ! Query(cons)
      val reply = expectMsgType[QueryResult]
      assert(reply.nodes.size == 1)
      assert(reply.nodes(0).id == aliceId)

//      expectMsgPF() {
//        case "Scala" => // only care that the PF is defined
//        case "Akka" =>
//      }

    }
  }
}