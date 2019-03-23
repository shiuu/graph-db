package graphdb

import java.io.File
import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import graphdb.models.FieldType
import graphdb.models.GraphDbDef.{HORConstraint, NodeConstraint, RConstraint}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, WordSpecLike}

class GraphDbStoreSpec extends TestKit(ActorSystem("GraphDbStoreSpec", ConfigFactory.load().getConfig("unitTest")))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterEach
  with BeforeAndAfterAll {

  var dbActor: ActorRef = _
  var aliceId: UUID = _
  var bobId: UUID = _
  var chenId: UUID = _
  var danId: UUID = _
  var telstraId: UUID = _
  var qantasId: UUID = _
  var tntId: UUID = _
  var employedByRid: Int = -1

  override def beforeAll(): Unit = {
    // delete the test journal
    val config = ConfigFactory.load().getConfig("unitTest")
    val testJournalPath = config.getString("akka.persistence.journal.leveldb.dir")
    val testJournal = new File(config.getString("akka.persistence.journal.leveldb.dir"))

    println(s"test journal configured to $testJournalPath")

    if (testJournal.exists()) {
      println(s"test journal folder already exists\nDeleting test journal")
      delete(testJournal)
      println("test journal deleted")
    }

    def delete(file: File) {
      if (file.isDirectory) {
        val fileArr = file.listFiles()
        if (fileArr != null)
          fileArr.foreach(delete(_))
      }
      file.delete
    }
  }

  override def beforeEach(): Unit = {
    dbActor = system.actorOf(Props[GraphDbStore])
  }

  override def afterEach(): Unit = {
    system.stop(dbActor)
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A GraphDbStore actor" should {
    "create Employee type" in {
      val msg = CreateType("Employee", Map("Name" -> FieldType.Str, "Age" -> FieldType.Number))
      dbActor ! msg

      expectMsgType[OperationSuccess]
    }
    "create Business type" in {
      dbActor ! CreateType("Business", Map("Name" -> FieldType.Str))
      expectMsgType[OperationSuccess]

      dbActor ! AddAttToType("Business", "Location", FieldType.Str)
      expectMsgType[OperationSuccess]
    }
    "Cannot create a type that already exist" in {
      dbActor ! CreateType("Business", Map())
      expectMsgType[OperationFailure]
    }
    "Add employees and verify" in {
      dbActor ! AddNode("Employee", Map("Name" -> "Alice", "Age" -> 38))
      val aliceMsg = expectMsgType[ObjectCreationSuccess]
      aliceId = aliceMsg.id

      dbActor ! AddNode("Employee", Map("Name" -> "Bob", "Age" -> 48))
      val bobMsg = expectMsgType[ObjectCreationSuccess]
      bobId = bobMsg.id

      dbActor ! AddNode("Employee", Map("Name" -> "Chen", "Age" -> 48))
      val chenMsg = expectMsgType[ObjectCreationSuccess]
      chenId = chenMsg.id

      dbActor ! AddNode("Employee", Map("Name" -> "Dan", "Age" -> 48))
      val danMsg = expectMsgType[ObjectCreationSuccess]
      danId = danMsg.id

      // get all employees
      val empCons = NodeConstraint("Employee",
        Map("_Type" -> "Employee"))

      dbActor ! Query(empCons)

      val empReply = expectMsgType[QueryResult]
      // verify that four employees are in the result: Alice, Bob, Chen and Dan
      assert(empReply.nodes.size == 4)
      val repliedEmployeeIds = empReply.nodes.map(n => n.id)
      assert(repliedEmployeeIds.contains(aliceId))
      assert(repliedEmployeeIds.contains(bobId))
      assert(repliedEmployeeIds.contains(chenId))
      assert(repliedEmployeeIds.contains(danId))
    }
    "Add Friend-Of relation and make friends" in {
      // Define Friend-Of relation
      dbActor ! CreateRelation("FriendOf", "Employee", List("Employee"))
      val friendship = expectMsgType[RelationCreationSuccess]

      // Link Bob to Alice with Friend-Of Relation
      dbActor ! LinkMsg(bobId, friendship.rid, List(aliceId))
      expectMsgType[ObjectCreationSuccess]

      // Link Bob to Dan with Friend-Of Relation
      dbActor ! LinkMsg(bobId, friendship.rid, List(danId))
      expectMsgType[ObjectCreationSuccess]

      val cons = NodeConstraint("Employee",
        Map("_OnLinkTarget" -> RConstraint(friendship.rid, Some(bobId), Seq.empty[UUID])))

      dbActor ! Query(cons)
      val reply = expectMsgType[QueryResult]
      // verify that bob has two friends: Alice and Dan
      assert(reply.nodes.size == 2)
      val repliedFriendIds = reply.nodes.map(n => n.id)
      assert(repliedFriendIds.contains(aliceId))
      assert(repliedFriendIds.contains(danId))
    }
    "Add Business nodes" in {
      // Add Business node Telstra, Qantas and TNT
      dbActor ! AddNode("Business", Map("Name" -> "Telstra", "Location" -> "CBD"))
      val telstraMsg = expectMsgType[ObjectCreationSuccess]
      telstraId = telstraMsg.id
      dbActor ! AddNode("Business", Map("Name" -> "Qantas", "Location" -> "Mascot"))
      val qantasMsg = expectMsgType[ObjectCreationSuccess]
      qantasId = qantasMsg.id
      dbActor ! AddNode("Business", Map("Name" -> "TNT", "Location" -> "Mascot"))
      val tntMsg = expectMsgType[ObjectCreationSuccess]
      tntId = tntMsg.id
    }
    "Define EmployedBy relation and list all employed person" in {
      dbActor ! CreateRelation("EmployedBy", "Employee", List("Business"))
      val employedBy = expectMsgType[RelationCreationSuccess]
      employedByRid = employedBy.rid

      // Alice works for Telstra
      dbActor ! LinkMsg(aliceId, employedBy.rid, List(telstraId))
      expectMsgType[ObjectCreationSuccess]

      // Bob works for Qantas
      dbActor ! LinkMsg(bobId, employedBy.rid, List(qantasId))
      expectMsgType[ObjectCreationSuccess]

      // Dan works for Qantas
      dbActor ! LinkMsg(danId, employedBy.rid, List(qantasId))
      expectMsgType[ObjectCreationSuccess]

      // List all employed person
      val empCons = NodeConstraint("Employee",
        Map("_OnLinkSrc" -> RConstraint(employedBy.rid, None, Seq.empty[UUID])))

      dbActor ! Query(empCons)

      val empReply = expectMsgType[QueryResult]
      // verify that three employees are in the result: Alice, Bob and Dan
      assert(empReply.nodes.size == 3)
      val repliedEmployeeIds = empReply.nodes.map(n => n.id)
      assert(repliedEmployeeIds.contains(aliceId))
      assert(repliedEmployeeIds.contains(bobId))
      assert(repliedEmployeeIds.contains(danId))
    }
    "Query all persons employed by a business located in Mascot" in {

      // Chen works for TNT
      dbActor ! LinkMsg(chenId, employedByRid, List(tntId))
      expectMsgType[ObjectCreationSuccess]

      // Query all persons employed by a business located in Mascot
      val locationCons = NodeConstraint("Business",
        Map("Location" -> "Mascot"))
      val empLocCons = NodeConstraint("Employee",
        Map("_OnLinkSrc" -> HORConstraint(employedByRid, None, locationCons)))

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
