package com.tubitv

import java.sql.Types.{ CHAR, LONGNVARCHAR, LONGVARCHAR, NCHAR, NVARCHAR, VARCHAR }

import scala.concurrent.ExecutionContext

import slick.jdbc.PostgresProfile
import slick.jdbc.meta._

class CodeGenPostgresProfile(config: CodeGenConfig) extends PostgresProfile {

  override def createModelBuilder(tables: Seq[MTable], ignoreInvalidDefaults: Boolean)(implicit ec: ExecutionContext): slick.jdbc.JdbcModelBuilder = new ModelBuilder(tables, ignoreInvalidDefaults)(ec) {

    val typeMapper: PartialFunction[String, String] = config.byTypeMapper orElse {
      case "name" | "text" | "varchar" => "String"
      case "int4" | "serial" => "Int"
      case "int2" | "smallserial" => "Short"
      case "int8" | "bigserial" | "oid" => "Long"
      case "bool" | "bit" => "Boolean"
    }

    val arraryDetector: PartialFunction[String, String] = {
      case a if a.startsWith("_") => a.substring(1)
    }

    override def createColumnBuilder(tableBuilder: TableBuilder, meta: MColumn): ColumnBuilder =
      new ColumnBuilder(tableBuilder, meta) {
        override def tpe =
          config.byNameMapper
            .lift(meta.table.name -> meta.name)
            .orElse(typeMapper.lift(meta.typeName))
            .orElse(arraryDetector.andThen(typeMapper).andThen(a => s"List[$a]").lift(meta.typeName))
            .getOrElse({
              val rt = super.tpe
              if (rt == "String" && !isJdbcStringType(meta.sqlType)) {
                logger.warn(
                  s"Column [${meta.name}] in table [${meta.table.name}] with type [${meta.typeName}] " +
                    s"does not have a custom mapping, so defaults map as [String], consider defining a custom type mapper")
              }
              rt
            })
      }

    override def readColumns(t: MTable): api.DBIO[Vector[MColumn]] = super.readColumns(t).map {
      v =>
        v.filterNot(m => config.ignoredColumns(m.table.name, m.name))
    }

    private def isJdbcStringType(sqlType: Int) = sqlType match {
      case CHAR | VARCHAR | LONGVARCHAR | NCHAR | NVARCHAR | LONGNVARCHAR => true
      case _ => false
    }
  }
}
