package graphdb

import graphdb.models.GraphDbDef.{Constraint, Node}

final case class Query(constraint: Constraint)
final case class QueryResult(nodes: Seq[Node])