
crossScalaVersions := Seq("2.12.15", "2.13.8")

Global / onChangedBuildSource := ReloadOnSourceChanges

enablePlugins(CodegenPlugin)

slickCodegenOutputContainer := "Table"
slickCodegenOutputPackage := "com.demo"
//)

codeGen("etl")(
  slickCodegenOutputContainer := "Etl",
)
