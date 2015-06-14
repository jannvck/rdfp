package rdfp.persistence.ormlite.nestedmapset

import com.j256.ormlite.table.DatabaseTable
import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.dao.ForeignCollection
import rdfp.persistence.ormlite.HavingValue

@DatabaseTable(tableName = "values") protected class Value(second: SecondMapping, v: String, i: Int) extends HavingValue[String] {
  @DatabaseField(generatedId = true) val id = i
  @DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "secondKey", uniqueIndexName = "secondMapping", width = 4096) val secondKey = second
  @DatabaseField(columnName = "value", uniqueIndexName = "secondMapping", width = 4096) val value = v
  def this(k: SecondMapping, v: String) = this(k,v,0)
  def this() = this(null, null.asInstanceOf[String], 0) // required by ORMLite
}