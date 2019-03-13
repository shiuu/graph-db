package graphdb

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import graphdb.models.FieldType
import graphdb.models.GraphDbDef.{Constraint, HORConstraint, RConstraint}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class GraphDbStoreSpec extends TestKit(ActorSystem("GraphDbStoreSpec"))
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
    "create Business, then do required test of the assignment" in {
      dbActor ! CreateType("Business", Map("Name" -> FieldType.Str))
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
      val bobId = bobMsg.id

      dbActor ! AddNode("Employee", Map("Name" -> "Chen", "Age" -> 48))
      val chenMsg = expectMsgType[ObjectCreationSuccess]
      val chenId = chenMsg.id

      dbActor ! AddNode("Employee", Map("Name" -> "Dan", "Age" -> 48))
      val danMsg = expectMsgType[ObjectCreationSuccess]
      val danId = danMsg.id

      // Define Friend-Of relation
      dbActor ! CreateRelation("FriendOf", "Employee", List("Employee"))
      val friendship = expectMsgType[RelationCreationSuccess]

      // Link Bob to Alice with Friend-Of Relation
      dbActor ! LinkMsg(bobId, friendship.rid, List(aliceId))
      expectMsgType[ObjectCreationSuccess]

      // Link Bob to Dan with Friend-Of Relation
      dbActor ! LinkMsg(bobId, friendship.rid, List(danId))
      expectMsgType[ObjectCreationSuccess]

      val cons = Constraint("Employee",
        Map("_OnLinkTarget" -> RConstraint(friendship.rid, Some(bobId), Seq.empty[UUID])))

      dbActor ! Query(cons)
      val reply = expectMsgType[QueryResult]
      // verify that bob has two friends: Alice and Dan
      assert(reply.nodes.size == 2)
      val repliedFriendIds = reply.nodes.map(n => n.id)
      assert(repliedFriendIds.contains(aliceId))
      assert(repliedFriendIds.contains(danId))

      // Add Business node Telstra Qantas and TNT
      dbActor ! AddNode("Business", Map("Name" -> "Telstra", "Location" -> "CBD"))
      val telstraMsg = expectMsgType[ObjectCreationSuccess]
      dbActor ! AddNode("Business", Map("Name" -> "Qantas", "Location" -> "Mascot"))
      val qantasMsg = expectMsgType[ObjectCreationSuccess]
      dbActor ! AddNode("Business", Map("Name" -> "TNT", "Location" -> "Mascot"))
      val tntMsg = expectMsgType[ObjectCreationSuccess]

      // Define EmployedBy relation
      dbActor ! CreateRelation("EmployedBy", "Employee", List("Business"))
      val employedBy = expectMsgType[RelationCreationSuccess]

      // Alice works for Telstra
      dbActor ! LinkMsg(aliceId, employedBy.rid, List(telstraMsg.id))
      expectMsgType[ObjectCreationSuccess]

      // Bob works for Qantas
      dbActor ! LinkMsg(bobId, employedBy.rid, List(qantasMsg.id))
      expectMsgType[ObjectCreationSuccess]

      // Dan works for Qantas
      dbActor ! LinkMsg(danId, employedBy.rid, List(qantasMsg.id))
      expectMsgType[ObjectCreationSuccess]

      // List all employed person
      val empCons = Constraint("Employee",
        Map("_OnLinkSrc" -> RConstraint(employedBy.rid, None, Seq.empty[UUID])))

      dbActor ! Query(empCons)

      val empReply = expectMsgType[QueryResult]
      // verify that three employees are in the result: Alice, Bob and Dan
      assert(empReply.nodes.size == 3)
      val repliedEmployeeIds = empReply.nodes.map(n => n.id)
      assert(repliedEmployeeIds.contains(aliceId))
      assert(repliedEmployeeIds.contains(bobId))
      assert(repliedEmployeeIds.contains(danId))

      // Chen works for TNT
      dbActor ! LinkMsg(chenId, employedBy.rid, List(tntMsg.id))
      expectMsgType[ObjectCreationSuccess]

      // Query all persons employed by a business located in Mascot
      val locationCons = Constraint("Business",
        Map("Location" -> "Mascot"))
      val empLocCons = Constraint("Employee",
        Map("_OnLinkSrc" -> HORConstraint(employedBy.rid, None, locationCons)))

      dbActor ! Query(empLocCons)

      val empLocReply = expectMsgType[QueryResult]
      // verify that three employees are in the result: Bob, Chen and Dan
      assert(empLocReply.nodes.size == 3)

      val repliedEmployeeIds2 = empLocReply.nodes.map(n => n.id)
      assert(repliedEmployeeIds2.contains(chenId))
      assert(repliedEmployeeIds2.contains(bobId))
      assert(repliedEmployeeIds2.contains(danId))
    }
  }
}