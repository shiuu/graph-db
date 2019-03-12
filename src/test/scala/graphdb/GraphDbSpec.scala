package graphdb

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import graphdb.GraphDb.GraphDbStore
import graphdb.GraphDb.GraphDbStore._
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class GraphDbSpec extends TestKit(ActorSystem("GraphDbSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override def afterAll(): Unit = { // teardown method
    TestKit.shutdownActorSystem(system)
  }

  "A GraphDb actor" should { // test suite
    "create Employee type" in { // test case
      val dbActor = system.actorOf(Props[GraphDbStore])
      val msg = CreateType("Employee", Map())
      dbActor ! msg

      val reply = expectMsgType[CreationSuccess]
      assert(reply.name == "Employee")
    }
  }
}