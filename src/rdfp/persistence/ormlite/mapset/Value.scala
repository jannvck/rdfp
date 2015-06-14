package rdfp.persistence.ormlite.mapset

import com.j256.ormlite.table.DatabaseTable
import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.dao.ForeignCollection
import rdfp.persistence.ormlite.HavingValue

@DatabaseTable(tableName = "values") protected class Value(k: Key, v: String, i: Int) extends HavingValue[String] {
  @DatabaseField(generatedId = true) val id = i
  @DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "key", uniqueIndexName = "mapping", width = 4096) val key = k
  @DatabaseField(columnName = "value", uniqueIndexName = "mapping", width = 4096) val value = v
  def this(k: Key, v: String) = this(k,v,0)
  def this() = this(null, null.asInstanceOf[String], 0) // required by ORMLite
}