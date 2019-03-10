package graphdb

import enumeratum._

sealed class FieldType extends EnumEntry
object FieldType extends Enum[FieldType]{
  val values = findValues

  case object Number  extends FieldType
  case object Bool    extends FieldType
  case object Str     extends FieldType
}