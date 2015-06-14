package rdfp.persistence.ormlite.nestedmapset

import com.j256.ormlite.table.DatabaseTable
import com.j256.ormlite.field.{ DatabaseField, ForeignCollectionField }
import com.j256.ormlite.dao.ForeignCollection
import rdfp.persistence.ormlite.HavingForeignCollection

@DatabaseTable(tableName = "secondMap") protected class SecondMapping(first: FirstMapping,
    second: String, v: ForeignCollection[Value], i: Int) extends HavingForeignCollection[Value] {
  @DatabaseField(generatedId = true) val id = i
  @DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "firstKey", uniqueIndexName = "firstMapping", width = 4096) val firstKey = first
  @DatabaseField(columnName = "secondKey", uniqueIndexName = "firstMapping", width = 4096) val secondKey = second
  @ForeignCollectionField(columnName = "values") val values = v
  def this(first: FirstMapping, second: String) = this(first,second,null,0)
  def this() = this(null, null, null, 0) // required by ORMLite
}