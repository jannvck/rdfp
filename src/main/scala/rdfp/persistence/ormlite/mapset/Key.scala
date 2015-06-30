package rdfp.persistence.ormlite.mapset

import com.j256.ormlite.table.DatabaseTable
import com.j256.ormlite.field.{ DatabaseField, ForeignCollectionField }
import com.j256.ormlite.dao.ForeignCollection

@DatabaseTable(tableName = "keys") protected class Key(k: String, fc: ForeignCollection[Value]) {
  @DatabaseField(columnName = "key", id = true, canBeNull = false, width = 4096) val key = k
  @ForeignCollectionField(columnName = "values") val values = fc // careful: raw access to ForeignCollection
  def this(k: String) = this(k, null)
  def this() = this(null.asInstanceOf[String],null) // required by ORMLite
}