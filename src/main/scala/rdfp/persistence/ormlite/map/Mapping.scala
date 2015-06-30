package rdfp.persistence.ormlite.map

import com.j256.ormlite.table.DatabaseTable
import com.j256.ormlite.field.{ DatabaseField, ForeignCollectionField, DataType }
import com.j256.ormlite.dao.ForeignCollection

@DatabaseTable(tableName = "mappings") protected class Mapping(k: String, v: String) {
  @DatabaseField(columnName = "key", id = true, canBeNull = false, width = 4096) val key = k
  @DatabaseField(columnName = "value", dataType = DataType.LONG_STRING) val value = v
  def this(k: String) = this(k, null)
  def this() = this(null.asInstanceOf[String],null) // required by ORMLite
}