package rdfp.persistence.ormlite.nestedmapset

import com.j256.ormlite.table.DatabaseTable
import com.j256.ormlite.field.{ DatabaseField, ForeignCollectionField }
import com.j256.ormlite.dao.ForeignCollection
import rdfp.persistence.ormlite.HavingForeignCollection

@DatabaseTable(tableName = "firstMap") protected class FirstMapping(k: String,
    fc: ForeignCollection[SecondMapping]) extends HavingForeignCollection[SecondMapping] {
  @DatabaseField(columnName = "key", id = true, canBeNull = false, width = 4096) val key = k
  @ForeignCollectionField(columnName = "secondKeys") val values = fc // careful: raw access to ForeignCollection
  def this(k: String) = this(k, null)
  def this() = this(null.asInstanceOf[String],null) // required by ORMLite
}