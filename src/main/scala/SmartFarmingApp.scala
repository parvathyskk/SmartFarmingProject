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
 

  // Get the latest reading
  def getLatestReading(): Option[Document] = {
    val future = collection.find().sort(Document("timestamp" -> -1)).first().headOption()
    Await.result(future, 5.seconds)
  } 
  def predict_water(): String = {
    val future = collection.find()
      .sort(Document("timestamp" -> -1))
      .first()
      .headOption()
      
    Await.result(future, 5.seconds) match {
      case Some(doc) =>   
        // Convert Document to JSON string
        doc.toJson()
        
      case None => 
        "{}" // Return empty JSON if no document found
    }
  }


  //converting scala ouput to python input 

  def getAndPredict(): String = {
  val jsonData = predict_water() // Your existing Scala function
  
  val command = Seq("python", "predict_handler.py")
  val output = (command #< new ByteArrayInputStream(jsonData.getBytes)).!!
  
  output.trim
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
