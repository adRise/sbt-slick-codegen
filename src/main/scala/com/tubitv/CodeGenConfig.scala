package com.tubitv

case class CodeGenConfig(
  byNameMapper: PartialFunction[(String, String), String] = PartialFunction.empty,
  byTypeMapper: PartialFunction[String, String] = PartialFunction.empty,
  ignoredColumns: ((String, String)) => Boolean = _ => false,
  profile: String = "slick.jdbc.PostgresProfile",
  // some extra imports need for the generated code
  extraImports: Seq[String] = Seq.empty,
  // extra customize code before the generated code, after the imports block
  extraHeadCode: Option[String] = None,
  // extra customize code after the generated code
  extraTailCode: Option[String] = None,
)
