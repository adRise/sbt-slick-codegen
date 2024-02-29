package com.tubitv

import slick.{model => m}
import slick.codegen.SourceCodeGenerator

class CustomizeSourceCodeGenerator(model: m.Model, config: CodeGenConfig) extends SourceCodeGenerator(model) {
  override def packageCode(profile: String, pkg: String, container: String, parentType: Option[String]): String = {
    def customizeHeadCode = config.extraHeadCode.map(code => s"\n$code\n").getOrElse("")
    def customizeTailCode = config.extraTailCode.map(code => s"\n$code\n").getOrElse("")
    s"""
       |package $pkg
       |
       |// format: off
       |// AUTO-GENERATED Slick data model
       |// scalastyle:off
       |/**
       | * Stand-alone Slick data model for immediate use
       | * Please do not touch this file manually, use slick-codegen
       | */
       |object $container extends {
       |  ${indent(config.extraImports.map(i => s"import ${i}").mkString("\n") + customizeHeadCode)}
       |
       |  val profile = ${config.profile}
       |  import profile.api._
       |
       |  ${indent(code + customizeTailCode)}
       |}""".stripMargin.trim()

  }
}
