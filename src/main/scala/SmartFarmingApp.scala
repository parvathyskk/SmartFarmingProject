import org.mongodb.scala._
import org.mongodb.scala.bson.{BsonString, BsonInt32, BsonDouble, BsonInt64, BsonValue}
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates.set
import java.io.ByteArrayInputStream
import scala.language.postfixOps
import sys.process._


object SmartFarmingApp {

  val mongoClient: MongoClient = MongoClient("mongodb+srv://blank:asdfghjkl@cluster0.tphdp3x.mongodb.net/")
  val database: MongoDatabase = mongoClient.getDatabase("smart_farming_db")
  val collection: MongoCollection[Document] = database.getCollection("sensor_data")

  // Insert a new reading
  def insertReading(
    soilMoisture: Double,
    temperature: Double,
    air_humidity: Double,
    ph: Double,
    rainfall: Double,
    n: Int,
    p: Int,
    k: Int
  ): Unit = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val timestampStr = ZonedDateTime.now(ZoneId.systemDefault()).format(formatter)

    val reading = Document(
      "soil_moisture" -> soilMoisture,
      "temperature" -> temperature,
      "timestamp" -> timestampStr,
      "air_humidity" -> air_humidity,
      "ph" -> ph,
      "rainfall" -> rainfall,
      "N" -> n,
      "P" -> p,
      "K" -> k
    )

    val insertFuture = collection.insertOne(reading).toFuture()
    Await.result(insertFuture, 5.seconds)
  }

  
 

  // Get the latest reading
  def getLatestReading(): Option[Document] = {
    val future = collection.find().sort(Document("timestamp" -> -1)).first().headOption()
    Await.result(future, 5.seconds)
  } 



  def predict_water(): String = {
  try {
    val future = collection.find()
      .sort(Document("timestamp" -> -1))
      .first()
      .headOption()
    
    Await.result(future, 5.seconds) match {
      case Some(doc) =>
        val jsonData = doc.toJson()
        println(s"[DEBUG] Sending to Python:\n$jsonData")
        
        val command = Seq("python", "D:/smart_water/SmartFarmingProject/src/main/script/predict_handler.py")
        val process = Process(command)
        val output = (process #< new ByteArrayInputStream(jsonData.getBytes("UTF-8"))).!!
        println(s"[DEBUG] Python output:\n$output")
        output
        
      case None => 
        """{"error": "No data found"}"""
    }
  } catch {
    case e: Exception =>
      println(s"[ERROR] Prediction failed: ${e.getMessage}")
      e.printStackTrace()
      """{"error": "Prediction processing failed"}"""
  }
}

  // Get all sensor data
  def readAllDataAsList(): Seq[Document] = {
    val futureDocs = collection.find().toFuture()
    Await.result(futureDocs, 10.seconds)
  }

  // Update a field by timestamp
  def updateField(timestamp: String, fieldName: String, value: BsonValue): Boolean = {
    val updateFuture = collection.updateOne(
      equal("timestamp", timestamp),
      set(fieldName, value)
    ).toFuture()

    val result = Await.result(updateFuture, 5.seconds)
    result.getModifiedCount > 0
  }

  // Delete a record by timestamp
  def deleteByTimestamp(timestamp: String): Boolean = {
    val resultFuture = collection.deleteOne(Document("timestamp" -> timestamp)).toFuture()
    val result = Await.result(resultFuture, 5.seconds)
    result.getDeletedCount > 0
  }
}
