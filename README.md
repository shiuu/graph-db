[graph-db]

**Note**
- The GraphDbStore is capable of recovering its state in a case of a restart (implemented using Akka Persistence).
- This implmentation is not cluster-ready yet. But has some initial configuration in build.sbt and application.conf, and some initial code (commented out) in the end of GraphDbStore.scala
- Unit test code is src/test/scala/graphdb/GraphDbStoreSpec. If you need to run test multiple times, please delete the target/journal folder before each run.
