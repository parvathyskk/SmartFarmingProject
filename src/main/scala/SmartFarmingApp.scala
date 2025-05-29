import org.mongodb.scala._
import org.mongodb.scala.bson.collection.mutable.Document
import java.time.Instant
import scala.io.{Source, StdIn}
import scala.concurrent.Await
import scala.concurrent.duration._

object SmartFarmingApp {

  val mongoClient: MongoClient = MongoClient("mongodb+srv://blank:asdfghjkl@cluster0.tphdp3x.mongodb.net/")
  val database: MongoDatabase = mongoClient.getDatabase("smart_farming_db")
  val collection: MongoCollection[Document] = database.getCollection("sensor_data")

  val MOISTURE_THRESHOLD = 30.0
  val PH_THRESHOLD = 5.5
  val AIR_THRESHOLD = 40.0
  val N_THRESHOLD = 50    
  val P_THRESHOLD = 30    
  val K_THRESHOLD = 40   




def getDoubleField(doc: Document, key: String): Option[Double] = {
  doc.get(key) match {
    case Some(bsonValue) if bsonValue.isDouble =>
      Some(bsonValue.asDouble().getValue)
    case Some(bsonValue) if bsonValue.isInt32 =>
      Some(bsonValue.asInt32().getValue.toDouble)
    case Some(bsonValue) if bsonValue.isInt64 =>
      Some(bsonValue.asInt64().getValue.toDouble)
    case _ => None
  }
}

def getStringField(doc: Document, key: String): Option[String] = {
  doc.get(key) match {
    case Some(bsonValue) if bsonValue.isString =>
      Some(bsonValue.asString().getValue)
    case _ => None
  }
}
  // Helper parsers that return Option types
  def parseDouble(s: String): Option[Double] = {
    if (s == null || s.trim.isEmpty) None
    else try Some(s.toDouble) catch { case _: NumberFormatException => None }
  }

  def parseInt(s: String): Option[Int] = {
    if (s == null || s.trim.isEmpty) None
    else try Some(s.toInt) catch { case _: NumberFormatException => None }
  }

  def parseString(s: String): Option[String] = {
    if (s == null || s.trim.isEmpty) None else Some(s)
  }

  def insertReading(soilMoisture: Double, temperature: Double, air_humidity: Double, 
  ph: Double, rainfall: Double, n:Int, p:Int, k:Int): Unit = {

    val reading = Document(
      "soil_moisture" -> soilMoisture,
      "temperature" -> temperature,
      "timestamp" -> Instant.now().toString,
      "air_humidity" -> air_humidity,
      "ph" -> ph,
      "rainfall" ->rainfall,
      "N" -> n,
      "P" -> p,
      "K" -> k
    )

    val insertFuture = collection.insertOne(reading).toFuture()
    Await.result(insertFuture, 5.seconds)
    println(" Manual sensor data inserted.")
  }

  def insertFromCSV(filePath: String): Unit = {
    val source = Source.fromFile(filePath)
    val lines = source.getLines().drop(1).take(10) // skip header, take 100 rows

    for (line <- lines) {
      val parts = line.split(",").map(_.trim)
      if (parts.length == 15) {
        val doc = Document()

        parseDouble(parts(0)).foreach(m => doc += "soil_moisture" -> m)
        parseDouble(parts(1)).foreach(t => doc += "temperature" -> t)
        parseString(parts(3)).foreach(ts => doc += "timestamp" -> ts)
        parseDouble(parts(4)).foreach(ah => doc += "air_humidity" -> ah)
        parseDouble(parts(9)).foreach(ph => doc += "ph" -> ph)
        parseDouble(parts(10)).foreach(rf => doc += "rainfall" -> rf)
        parseInt(parts(11)).foreach(n => doc += "N" -> n)
        parseInt(parts(12)).foreach(p => doc += "P" -> p)
        parseInt(parts(13)).foreach(k => doc += "K" -> k)
        // parseDouble(parts(4)).foreach(at => doc += "air_temperature" -> at)
        // parseDouble(parts(5)).foreach(ws => doc += "wind_speed" -> ws)
        
        // parseDouble(parts(7)).foreach(wg => doc += "wind_gust" -> wg)
        // parseDouble(parts(8)).foreach(p => doc += "pressure" -> p)
       
        // parseString(parts(14)).foreach(st => doc += "status" -> st)

        val insertFuture = collection.insertOne(doc).toFuture()
        Await.result(insertFuture, 30.seconds)
        println(s"Inserted row with timestamp ${parts(2)}")
      } else {
        println(s"Skipping invalid row: $line")
      }
    }

    source.close()
  }

  def getLatestReading(): Option[Document] = {
    val future = collection
      .find()
      .sort(Document("timestamp" -> -1))
      .first()
      .headOption()
      // timeout duration when waiting on futures:
      Await.result(future, 5.seconds) 
  }

  def checkWatering(): Unit = {
  getLatestReading() match {
    case Some(doc) =>
      val moisture = getDoubleField(doc, "soil_moisture").getOrElse(0.0)
      val temp = getDoubleField(doc, "temperature").getOrElse(0.0)
      val timestamp = getStringField(doc, "timestamp").getOrElse("Unknown")
      val airHumidity = getDoubleField(doc, "air_humidity").getOrElse(0.0)
      val ph = getDoubleField(doc, "ph").getOrElse(0.0)
      val rainfall = getDoubleField(doc, "rainfall").getOrElse(0.0)
      val n = getDoubleField(doc, "N").getOrElse(-1.0).toInt
      val p = getDoubleField(doc, "P").getOrElse(-1.0).toInt
      val k = getDoubleField(doc, "K").getOrElse(-1.0).toInt

      println()
      println(s"** Latest Reading **")
      println(s"   Soil Moisture: $moisture%")
      println(s"   Temperature: $temp Celcius")
      println(s"   Time: $timestamp")
      println(s"   Air Humidity: $airHumidity%")
      println(s"   pH Level: $ph")
      println(s"   Rainfall: $rainfall mm")
      println(s"   NPK Levels: N=$n, P=$p, K=$k")
      println()

      if (moisture < MOISTURE_THRESHOLD)
        println(" Watering Required: Soil moisture is too low.")
      else if (ph < PH_THRESHOLD)
        println(" Watering Required: Soil pH is too low.")
      else if (airHumidity < AIR_THRESHOLD)
        println(" Watering Required: Air humidity is too low.")
      else
        println(" No Watering Needed: All moisture-related values are healthy.")

      println()

      if (n < N_THRESHOLD) println(" Add Nitrogen: N level is too low.")
      if (p < P_THRESHOLD) println(" Add Phosphorus: P level is too low.")
      if (k < K_THRESHOLD) println(" Add Potassium: K level is too low.")
      if (n >= N_THRESHOLD && p >= P_THRESHOLD && k >= K_THRESHOLD)
        println(" NPK Levels are sufficient.")

    case None =>
      println(" No sensor data found.")
  }
}


  def main(args: Array[String]): Unit = {
    try {
      println(" Smart Farming Options:")
      println("1. Manual Entry")
      println("2. Upload CSV")
      print("Choose an option (1 or 2): ")
      val choice = StdIn.readLine()

      choice match {
        case "1" =>
          print("Enter Soil Moisture (%): ")
          val moisture = StdIn.readDouble()

          print("Enter Temperature (Celcius): ")
          val temperature = StdIn.readDouble()

          print("Enter Air Humidity (%): ")
          val air_humidity= StdIn.readDouble()

          print("Enter pH: ")
          val ph= StdIn.readDouble()

          print("Enter ranifall (%): ")
          val rainfall= StdIn.readDouble()

          print("Enter Nitrogen (N) level: ")
          val n = StdIn.readInt()

          print("Enter Phosphorus (P) level: ")
          val p = StdIn.readInt()

          print("Enter Potassium (K) level: ")
          val k = StdIn.readInt()


          insertReading(moisture, temperature,air_humidity, ph, rainfall, n, p, k)
          checkWatering()

        case "2" =>
          val csvPath = "TARP.csv" // Pre-uploaded file name
          insertFromCSV(csvPath)

        case _ =>
          println(" Invalid option.")
      }
    } finally {
      mongoClient.close()
    }
  }
}   