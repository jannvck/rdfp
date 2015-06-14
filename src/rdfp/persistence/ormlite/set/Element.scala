package rdfp.persistence.ormlite.set

import com.j256.ormlite.table.DatabaseTable
import com.j256.ormlite.field.{ DatabaseField, ForeignCollectionField }
import com.j256.ormlite.dao.ForeignCollection

@DatabaseTable(tableName = "elements") class Element(elem: String) {
  @DatabaseField(columnName = "element", id = true, canBeNull = false, width = 4096) val element = elem
  def this() = this(null) // required by ORMLite
}