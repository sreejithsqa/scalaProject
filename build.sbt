name := "sparkcourse"

version := "1.0"

scalaVersion := "2.11.8"

val sparkVersion = "2.2.0"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion
)

// https://mvnrepository.com/artifact/com.databricks/spark-csv
libraryDependencies += "com.databricks" %% "spark-csv" % "1.5.0"

libraryDependencies += "org.scalafx" %% "scalafx" % "8.0.144-R12"




