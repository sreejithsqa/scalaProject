package com.scala.spark

import java.lang.Math.{atan2, cos, sin, sqrt}

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{avg, count, max, monotonically_increasing_id}
import org.apache.spark.sql.types._
import scalafx.application.JFXApp
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Side
import scalafx.scene.Scene
import scalafx.scene.chart.{NumberAxis, ScatterChart, XYChart}


//case class fullData1(pid:Integer,date: String,country: String,province: String,city: String,lat: Double,Longitude: Double,pid1: String,Plat:Double,Plong: Double,id:Long,Distance:Double, pid2:String, mDistance:Double)



object Newcourse1  extends JFXApp {
  val spark = SparkSession.builder()
    .appName("Test")
    .master("local")
    .getOrCreate()

  import spark.implicits._

  spark.sparkContext.setLogLevel("Warn")

  val tschema = StructType(Array(
    StructField("pid", IntegerType),
    StructField("date", StringType),
    StructField("country", StringType),
    StructField("province", StringType),
    StructField("city", StringType),
    StructField("lat", DoubleType),
    StructField("longitude", DoubleType)
  ))

  val sschema = StructType(Array(
    StructField("pid1", StringType),
    StructField("Plat", DoubleType),
    StructField("Plong", DoubleType)
  ))

  val datasampleexcel = spark.read.schema(tschema).option("header", "true").csv("/Users/admin/Desktop/sparkcourse/Downloads/DataSample.csv")
  val datapoiexcel = spark.read.schema(sschema).option("header", "true").csv("/Users/admin/Desktop/sparkcourse/Downloads/POIList.csv")

  //datasampleexcel.show()
  println("the length of org csv is " + datasampleexcel.count())
  datasampleexcel.schema.printTreeString()
  val cleandatasample = datasampleexcel.dropDuplicates("lat", "longitude", "date")
  //cleandatasample.show
  println("the length after removing duplicate is  " + cleandatasample.count())

  //datapoiexcel.show()

  import scala.collection.mutable.ListBuffer;
  var distanceList = new ListBuffer[Double]();

  for (row <- cleandatasample.rdd.collect) {

    val lat1 = row.mkString(",").split(",")(5).toDouble
    val long1 = row.mkString(",").split(",")(6).toDouble
    var poi1 = "";
    var shortestDist1 = 0.0;
    for (rows <- datapoiexcel.rdd.collect) {
      val lat2 = rows.mkString(",").split(",")(1).toDouble
      val long2 = rows.mkString(",").split(",")(2).toDouble
      val curPoi = rows.mkString(",").split(",")(0);
      val curDistance1 = latLonDistance(lat1, long1)(lat2, long2) //.toDF("Distance")
      distanceList += curDistance1

    }

  }

  val finaljoined = cleandatasample.crossJoin(datapoiexcel)
  val finaljoinedwithid = finaljoined.withColumn("id", monotonically_increasing_id + 1).toDF()
  //finaljoinedwithid.show()
  //println("final count" + finaljoined.count())
  //println("yyyyyyyyy")
  val dist_List = distanceList.toList
  //println("Distance list" + distanceList.toList.length)
  val distdataframe = spark.createDataFrame(dist_List.toDF().rdd.map(_.getDouble(0)).zipWithIndex)
  //distdataframe.show()
  //println("distdataframe")

  val getiddataframe = spark.createDataFrame(finaljoinedwithid.rdd.map(_.getLong(10)).zipWithIndex)

  //getiddataframe.show()
  //println("d1nwew1")

  getiddataframe.join(distdataframe, distdataframe("_2") === getiddataframe("_2"))
  //getiddataframe.show()
  //println("getiddataframe after join")
  val dataJoinWithIndex = finaljoinedwithid.join(getiddataframe, finaljoinedwithid("id") === getiddataframe("_1")).drop("_1") //.withColumnRenamed("_2", "Distance")
  //dataJoinWithIndex.show()
  //println("dataJoinWithIndex finaljoined")

  val dataJoinedWithDist = dataJoinWithIndex.join(distdataframe, distdataframe("_2") === dataJoinWithIndex("_2")).drop("_2").withColumnRenamed("_1", "Distance")
  //dataJoinedWithDist.show()
  //println("dataJoinedWithDist ")

  val dataRenameColpid2 = dataJoinedWithDist.withColumnRenamed("pid", "pid2")
  //dataRenameColpid2.show()
  //println("dataRenameColpid2")
  val dataGrpByMinDist = dataRenameColpid2.groupBy("pid2").min("Distance")
  //dataGrpByMinDist.show()

  //println("dataGrpByMinDist groupy Distance")

  val finalResultdf = dataJoinedWithDist.join(dataGrpByMinDist, (dataJoinedWithDist("pid") === dataGrpByMinDist("pid2")) && (dataJoinedWithDist("Distance") === dataGrpByMinDist("min(Distance)"))).toDF()
  //println(" imp only one pid finalResultdf")
  finalResultdf.show()

  //To Show POIID Count to estimate the request Received
 /* var getCountperId1 =finalResultdf.filter(r =>(r.getString(7)=="POI1")).count()
  println("count of POI1 is "+getCountperId1)
  var getCountperId2 =finalResultdf.filter(r =>(r.getString(7)=="POI2")).count()
  println("count of POI2 is "+getCountperId2)
  var getCountperId3 =finalResultdf.filter(r =>(r.getString(7)=="POI3")).count()
  println("count of POI3 is "+getCountperId3)
  var getCountperId4 =finalResultdf.filter(r =>(r.getString(7)=="POI4")).count()
  println("count of POI4 is "+getCountperId4)
*/
  var finalResultDataRdd = finalResultdf.rdd.collect()

  val dataAvgWithOutliers = finalResultdf.groupBy("pid").agg(avg($"min(Distance)").alias("avg with outliers"))
  //dataAvgWithOutliers.show()
  //println("dataAvgWithOutliers")
  


  val dataWithStandardDev = finalResultdf.describe("min(Distance)")
  //dataWithStandardDev.show()

  

  //radius and desnity with outliers
  val radiuscalc = finalResultdf.groupBy("pid").agg(max("min(Distance)").alias("Radius"))
  radiuscalc.show()
  //println("radius")
  val densitycalc = finalResultdf.groupBy("pid").agg(count("min(Distance)").alias("Count"))
  densitycalc.show()



  def latLonDistance(lat1: Double, lon1: Double)(lat2: Double, lon2: Double): Double = {
    val earthRadiusKm = 6371
    val dLat = (lat2 - lat1).toRadians
    val dLon = (lon2 - lon1).toRadians
    val latRad1 = lat1.toRadians
    val latRad2 = lat2.toRadians

    val a = sin(dLat / 2) * sin(dLat / 2) + sin(dLon / 2) * sin(dLon / 2) * cos(latRad1) * cos(latRad2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    earthRadiusKm * c
    // println(c)
    return c

  }



  spark.stop()

  stage = new JFXApp.PrimaryStage {
    title = "ScatterChartDemo"
    scene = new Scene(900, 900) {
      stylesheets.add("AdvCandleStickChartSample.css")
      root = new ScatterChart(NumberAxis("X", -120, 150, 5), NumberAxis("Y", -120, 150, 10)) {
        title = "Scatter Chart"
        legendSide = Side.Bottom

        val pData = XYChart.Series[Number, Number]("TEMPS",
          ObservableBuffer(finalResultDataRdd.map(td => XYChart.Data[Number, Number](td.getDouble(5), td.getDouble(6))): _*))

        data = ObservableBuffer(
          xySeries("Data Sample Geo Location", finalResultDataRdd.map(tc => (tc.getDouble(5), tc.getDouble(6)))),
          xySeries("Poi GeoLoc 1", Seq((53, -113))),
          xySeries("Poi GeoLoc 2", Seq((45, -73))),
          xySeries("Poi GeoLoc 3", Seq((45, -63))))
        //xySeries("Data Sample Geo Location", finalResultDataRdd.map(tc => (tc.getDouble(5), tc.getDouble(6))))


      }
    }
  }

  /** Create XYChart.Series from a sequence of number pairs. */
  def xySeries(name: String, data: Seq[(Double, Double)]) =
    XYChart.Series[Number, Number](
      name,
      ObservableBuffer(data.map { case (x, y) => XYChart.Data[Number, Number](x, y) })
    )
}
