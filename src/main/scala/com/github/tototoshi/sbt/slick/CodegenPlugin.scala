package com.github.tototoshi.sbt.slick

import sbt.*

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.io.Source
import scala.util.Using

import Keys.*
import com.tubitv.{CodeGenConfig, CodeGenPostgresProfile, CustomizeSourceCodeGenerator, PostgresContainer}
import slick.{model => m}
import slick.codegen.SourceCodeGenerator
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.JdbcProfile

object CodegenPlugin extends sbt.AutoPlugin with PluginDBSupport {
  private val createdConfigs =
    settingKey[Set[Configuration]]("The configurations for the config, other than default")

  object autoImport {
    lazy val slickCodegen: TaskKey[Seq[File]] = taskKey[Seq[File]]("Command to run codegen")
    lazy val slickCodegenAll: TaskKey[Unit] = taskKey[Unit]("Command to run all the codegen")
    lazy val slickCodegenVerify: TaskKey[Unit] = taskKey("Verify if the generated code is out of date")
    lazy val slickCodegenVerifyAll: TaskKey[Unit] = taskKey("Verify any of the generated code is out of date")

    lazy val slickCodegenConfig: SettingKey[CodeGenConfig] =
      settingKey("The configuration for the code generator")

    lazy val slickCodegenDriver: SettingKey[JdbcProfile] =
      settingKey[JdbcProfile]("Slick driver used by codegen")

    lazy val slickCodegenJdbcDriver: SettingKey[String] =
      settingKey[String]("Jdbc driver used by codegen")

    lazy val slickCodegenOutputPackage: SettingKey[String] =
      settingKey[String]("Package of generated code")

    lazy val slickCodegenOutputFile: SettingKey[String] =
      settingKey[String]("Generated file")

    lazy val slickCodegenOutputToMultipleFiles: SettingKey[Boolean] =
      settingKey[Boolean]("Write generated code to multiple files instead of a single file")

    lazy val slickCodegenOutputDir: SettingKey[File] =
      settingKey[File]("Folder where the generated file lives in")

    lazy val slickCodegenOutputContainer: SettingKey[String] =
      settingKey[String]("Container of generated source code")

    lazy val slickCodegenCodeGenerator: SettingKey[m.Model => SourceCodeGenerator] =
      settingKey[m.Model => SourceCodeGenerator]("Function to create CodeGenerator to be used")

    lazy val slickCodegenExcludedTables: SettingKey[Seq[String]] =
      settingKey[Seq[String]]("Tables that should be excluded")

    lazy val slickCodegenIncludedTables: SettingKey[Seq[String]] =
      settingKey[Seq[String]](
        "Tables that should be included. If this list is not nil, only the included tables minus excluded will be taken."
      )

    /** Define a new configuration scope for slick code gen,
      * for the cases where there are multiple codegen in a project
      * @param configName
      * @param setting
      * @return
      */
    def codeGen(configName: String)(setting: Setting[_]*): Seq[Setting[_]] = {
      val theConfig = Configuration.of(configName.take(1).toUpperCase() + configName.drop(1), configName)
      Seq(createdConfigs += theConfig) ++ inConfig(theConfig)(defaultConfigs ++ setting)
    }
  }

  import autoImport._

  private def withGenerator[T](fun: SourceCodeGenerator => T) = Def.task {
    val generator = slickCodegenCodeGenerator.value
    val driver = slickCodegenDriver.value

    val database = driver.api.Database.forURL(
      url = postgresDbUrl.value,
      driver = slickCodegenJdbcDriver.value,
      user = dbUser,
      password = dbPass
    )

    try {
      database.source.createConnection().close()
    } catch {
      case e: Throwable =>
        throw new RuntimeException("Failed to run slick-codegen: " + e.getMessage, e)
    }

    val excluded = slickCodegenExcludedTables.value
    val included = slickCodegenIncludedTables.value

    val tables = driver.defaultTables
      .map(ts => ts.filter(t => included.isEmpty || (included contains t.name.name)))
      .map(ts => ts.filterNot(t => excluded contains t.name.name))

    val dbio: DBIOAction[T, NoStream, Effect.All] = for {
      m <- driver.createModel(Some(tables))
    } yield fun(generator(m))

    Await.result(database.run(dbio), Duration.Inf)
  }

  private def profile = Def.task {
    val driverClassName = slickCodegenDriver.value.getClass.getName
    // if it's a singleton object, then just reference it directly
    if (driverClassName.endsWith("$")) driverClassName.stripSuffix("$")
    // if it's an instance of a regular class, we don't know constructor args; try the no-arguments constructor and hope for the best
    else s"new $driverClassName()"
  }

  private def gen = Def.taskDyn {
    val p = profile.value
    val outDir = {
      val folder = slickCodegenOutputDir.value

      if (folder.exists()) {
        require(folder.isDirectory, s"file :[$folder] is not a directory")
      } else {
        folder.mkdir()
      }
      folder.getPath
    }
    val pkg = slickCodegenOutputPackage.value
    val fileName = slickCodegenOutputFile.value
    val container = slickCodegenOutputContainer.value

    val s = streams.value
    val outputToMultipleFiles = slickCodegenOutputToMultipleFiles.value

    Def.taskDyn {
      val _ = withGenerator {
        sourceGen =>
          if (outputToMultipleFiles) {
            sourceGen.writeToMultipleFiles(profile = p, folder = outDir, pkg = pkg, container = container)
          } else {
            sourceGen.writeToFile(profile = p, folder = outDir, pkg = pkg, container = container, fileName = fileName)
          }
      }.value

      Def.task {
        if (outputToMultipleFiles) {
          val outDirFile = file(outDir)
          s.log.info(s"Source code files have been generated in ${outDirFile.getAbsolutePath}")
          listScalaFileRecursively(outDirFile)
        } else {
          val generatedFile = outDir + "/" + pkg.replaceAllLiterally(".", "/") + "/" + fileName
          s.log.info(s"Source code has generated in ${generatedFile}")
          Seq(file(generatedFile))
        }
      }
    }
  }

  private def verify = Def.taskDyn {
    val p = profile.value
    val pkg = slickCodegenOutputPackage.value
    val container = slickCodegenOutputContainer.value

    val theConf = configuration.?.value
    val s = streams.value

    withGenerator {
      sourceGen =>
        val theCode = sourceGen.packageCode(profile = p, pkg = pkg, container = container, sourceGen.parentType)
        val file = slickCodegenOutputDir.value + "/" + pkg.replace(".", "/") + "/" + slickCodegenOutputFile.value
        val generated = Using(Source.fromFile(file))(_.mkString).get
        if (theCode.trim != generated.trim) {
          throw new Exception(
            s"Schema file: $file is out of date, please regenerate it by [ ${theConf.map(c => c.name + " / ").getOrElse("")}slickCodegen ]"
          )
        }
        s.log.info(s"Verify schema $file success")
    }
  }

  private def defaultConfigs = Seq(
    slickCodegenConfig := CodeGenConfig(),
    slickCodegenDriver := new CodeGenPostgresProfile(slickCodegenConfig.value),
    slickCodegenJdbcDriver := "org.postgresql.Driver",
    slickCodegenOutputPackage := "com.example",
    slickCodegenOutputFile := s"${slickCodegenOutputContainer.value}.scala",
    slickCodegenOutputToMultipleFiles := false,
    slickCodegenOutputDir := (Compile / Keys.scalaSource).value,
    slickCodegenOutputContainer := "Tables",
    slickCodegenExcludedTables := Seq(),
    slickCodegenIncludedTables := Seq(),
    slickCodegenCodeGenerator := { (m) => new CustomizeSourceCodeGenerator(m, slickCodegenConfig.value) },
    slickCodegen := gen.value,
    slickCodegenVerify := verify.value
  )

  override lazy val projectSettings: Seq[Setting[_]] =
    dbSettings ++ defaultConfigs ++
      Seq(
        createdConfigs := Set.empty,
        slickCodegenAll := withDb(Def.taskDyn {
          Def.sequential(slickCodegen +: createdConfigs.value.toList.map(c => c / slickCodegen))
        }).value,
        slickCodegenVerifyAll := withDb(Def.taskDyn {
          Def.sequential(slickCodegenVerify +: createdConfigs.value.toList.map(c => c / slickCodegenVerify))
        }).value,
      )

  private def listScalaFileRecursively(dir: File): Seq[File] = {
    val buf = new ListBuffer[File]()
    def addFiles(d: File): Unit = {
      d.listFiles().foreach {
        f =>
          if (f.isDirectory) { addFiles(f) }
          else if (f.getName.endsWith(".scala")) { buf += f }
      }
    }
    addFiles(dir)
    buf.toSeq
  }

}
