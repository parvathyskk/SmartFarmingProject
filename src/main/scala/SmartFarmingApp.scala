import org.mongodb.scala._
import org.mongodb.scala.bson.{BsonString, BsonInt32, BsonDouble, BsonInt64,BsonValue}
import java.time.Instant
import scala.io.{Source, StdIn}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import org.mongodb.scala.model.Filters.equal

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
      case Some(bsonValue: BsonDouble) => Some(bsonValue.getValue)
      case Some(bsonValue: BsonInt32) => Some(bsonValue.getValue.toDouble)
      case Some(bsonValue: BsonInt64) => Some(bsonValue.getValue.toDouble)
      case _ => None
    }
  }

  def getStringField(doc: Document, key: String): Option[String] = {
    doc.get(key) match {
      case Some(bsonValue: BsonString) => Some(bsonValue.getValue)
      case _ => None
    }
  }

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
                    ph: Double, rainfall: Double, n: Int, p: Int, k: Int): Unit = {
    val reading = org.mongodb.scala.Document(
      "soil_moisture" -> soilMoisture,
      "temperature" -> temperature,
      "timestamp" -> Instant.now().toString,
      "air_humidity" -> air_humidity,
      "ph" -> ph,
      "rainfall" -> rainfall,
      "N" -> n,
      "P" -> p,
      "K" -> k
    )

    val insertFuture = collection.insertOne(reading).toFuture()
    Await.result(insertFuture, 5.seconds)
    println("Manual sensor data inserted.")
  }

  def insertFromCSV(filePath: String): Unit = {
  val source = Source.fromFile(filePath)
  val lines = source.getLines().drop(1).take(100) // Skip header, limit rows

  for (line <- lines) {
    val parts = line.split(",").map(_.trim)
    if (parts.length >= 14) {

      val fields = scala.collection.mutable.Map[String, BsonValue]()

      parseDouble(parts(0)).foreach(m => fields += "soil_moisture" -> BsonDouble(m))
      parseDouble(parts(1)).foreach(t => fields += "temperature" -> BsonDouble(t))
      parseString(parts(3)).foreach(ts => fields += "timestamp" -> BsonString(ts))
      parseDouble(parts(4)).foreach(ah => fields += "air_humidity" -> BsonDouble(ah))
      parseDouble(parts(9)).foreach(ph => fields += "ph" -> BsonDouble(ph))
      parseDouble(parts(10)).foreach(rf => fields += "rainfall" -> BsonDouble(rf))
      parseInt(parts(11)).foreach(n => fields += "N" -> BsonInt32(n))
      parseInt(parts(12)).foreach(p => fields += "P" -> BsonInt32(p))
      parseInt(parts(13)).foreach(k => fields += "K" -> BsonInt32(k))

      val doc = Document(fields.toMap)

      val insertFuture = collection.insertOne(doc).toFuture()
      Await.result(insertFuture, 10.seconds)
      println(s"Inserted row with timestamp ${parts(3)}")
    } else {
      println(s"Skipping invalid row: $line")
    }
  }

  source.close()
}

  def getLatestReading(): Option[Document] = {
    val future =  collection.find().sort(org.mongodb.scala.Document("timestamp" -> -1)).first().headOption()
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

        println("\n** LATEST READING **\n")
        println(f"   Soil Moisture: $moisture%.2f%%")
        println(f"   Temperature: $temp%.2f Cel")
        println(s"   Time: $timestamp")
        println(f"   Air Humidity: $airHumidity%.2f%%")
        println(f"   pH Level: $ph%.2f")
        println(f"   Rainfall: $rainfall%.2f mm")
        println(s"   NPK Levels: N=$n, P=$p, K=$k\n")

        if (moisture < MOISTURE_THRESHOLD)
          println("Watering Required: Soil moisture is too low.")
        else if (ph < PH_THRESHOLD)
          println("Watering Required: Soil pH is too low.")
        else if (airHumidity < AIR_THRESHOLD)
          println("Watering Required: Air humidity is too low.")
        else
          println("No Watering Needed: All moisture-related values are healthy.")

        if (n < N_THRESHOLD) println("Add Nitrogen: N level is too low.")
        if (p < P_THRESHOLD) println("Add Phosphorus: P level is too low.")
        if (k < K_THRESHOLD) println("Add Potassium: K level is too low.")
        if (n >= N_THRESHOLD && p >= P_THRESHOLD && k >= K_THRESHOLD)
          println("NPK Levels are sufficient.")
        println()

      case None =>
        println("No sensor data found.")
    }
  }

  // READ: Print all sensor data
def readAllData(): Unit = {
  val futureDocs = collection.find().toFuture()
  val documents = Await.result(futureDocs, 10.seconds)

  if (documents.isEmpty) {
    println("No sensor data found.")
  } else {
    println("\n** All Sensor Records **")
    documents.foreach { doc =>
      println(doc.toJson())
    }
  }
}

// UPDATE: Update specific fields of a record by timestamp
def update(timestamp: String): Unit = {
  println("\nWhich field do you want to update?\n")
  println("1. Soil Moisture\n2. Temperature\n3. Air Humidity\n4. pH\n5. Rainfall\n" +
    "6. Nitrogen (N)\n7. Phosphorus (P)\n8. Potassium (K)\nChoose an option (1-8): ")

  val fieldChoice = StdIn.readLine().trim
  val (fieldName, fieldTypeOpt) = fieldChoice match {
    case "1" => ("soil_moisture", "double")
    case "2" => ("temperature", "double")
    case "3" => ("air_humidity", "double")
    case "4" => ("ph", "double")
    case "5" => ("rainfall", "double")
    case "6" => ("N", "int")
    case "7" => ("P", "int")
    case "8" => ("K", "int")
    case _ =>
      println("Invalid option.")
      return
  }

  print(s"Enter new value for $fieldName: ")
  val newValueStr = StdIn.readLine().trim

  val newValue: BsonValue = fieldTypeOpt match {
    case "double" =>
      try {
        BsonDouble(newValueStr.toDouble)
      } catch {
        case _: NumberFormatException =>
          println("Invalid input. Expected a decimal number.")
          return
      }
    case "int" =>
      try {
        BsonInt32(newValueStr.toInt)
      } catch {
        case _: NumberFormatException =>
          println("Invalid input. Expected an integer.")
          return
      }
  }

  val updateFuture = collection.updateOne(
    equal("timestamp", timestamp),
    org.mongodb.scala.model.Updates.set(fieldName, newValue)).toFuture()

  val result = Await.result(updateFuture, 5.seconds)

  if (result.getModifiedCount > 0)
    println(s"Successfully updated $fieldName for timestamp $timestamp")
  else
    println(s"No document found with timestamp: $timestamp")
}


// DELETE: Delete a record by timestamp
def delete(timestamp: String): Unit = {
  val resultFuture = collection.deleteOne(org.mongodb.scala.Document("timestamp" -> timestamp)).toFuture()
  val result = Await.result(resultFuture, 5.seconds)

  if (result.getDeletedCount > 0) println("Record deleted successfully.")
  else println("No matching document found to delete.")
}


  def main(args: Array[String]): Unit = {
    try {
      println("** SAMRT FARMING OPTIONS: **\n")
      println("1. Manual Entry")
      println("2. Upload CSV")
      println("3. View All Sensor Data")
      println("4. Update a Record by Timestamp")
      println("5. Delete a Record by Timestamp")
      print("Choose an option (1 to 5): ")
      val choice = StdIn.readLine()

      choice match {
        case "1" =>
          print("Enter Soil Moisture (%): ")
          val moisture = StdIn.readDouble()

          print("Enter Temperature (Cel): ")
          val temperature = StdIn.readDouble()

          print("Enter Air Humidity (%): ")
          val air_humidity = StdIn.readDouble()

          print("Enter pH: ")
          val ph = StdIn.readDouble()

          print("Enter Rainfall (mm): ")
          val rainfall = StdIn.readDouble()

          print("Enter Nitrogen (N) level: ")
          val n = StdIn.readInt()

          print("Enter Phosphorus (P) level: ")
          val p = StdIn.readInt()

          print("Enter Potassium (K) level: ")
          val k = StdIn.readInt()

          insertReading(moisture, temperature, air_humidity, ph, rainfall, n, p, k)
          checkWatering()

        case "2" =>
          val csvPath = "TARP.csv"
          insertFromCSV(csvPath)
          checkWatering()
        case "3" =>
          readAllData()

        case "4" =>
          print("Enter timestamp of record to update: ")
          val ts = StdIn.readLine().trim
          update(ts)

        case "5" =>
          print("Enter timestamp of record to delete: ")
          val ts = StdIn.readLine().trim
          delete(ts)

        case _ =>
          println("Invalid option.")
      }
    } finally {
      mongoClient.close()
    }
  }
}