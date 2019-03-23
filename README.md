## Akka Graph DB

This Akka-based database allows users to store and query an arbitrary data in a form of a graph where the schema can also be defined by user.

**Note**
- The system is capable of recovering its state in a case of a restart or failure (implemented using Akka Persistence)
- The system provides a user with the way to create and modify the data
  * Create a data node of a given type
  * Add a new attribute to an existing node
  * Delete an attribute from an existing node
  * Establish a directional link between a two nodes in the system
- Unit test code is src/test/scala/graphdb/GraphDbStoreSpec.

