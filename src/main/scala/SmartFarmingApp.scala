import org.mongodb.scala._
import org.mongodb.scala.bson.{BsonString, BsonInt32, BsonDouble, BsonInt64, BsonValue}
import java.time.{ZonedDateTime, ZoneId}
import java.time.format.DateTimeFormatter
import scala.concurrent.{Await, Future, ExecutionContext, blocking}
import scala.concurrent.duration._
import org.mongodb.scala.model.{Filters, Updates}
import java.io.ByteArrayInputStream
import scala.sys.process._

object SmartFarmingApp {
  // MongoDB connection
  val mongoClient: MongoClient = MongoClient(
  "mongodb+srv://blank:asdfghjkl@cluster0.tphdp3x.mongodb.net/?connectTimeoutMS=180000&serverSelectionTimeoutMS=180000"
)

  val database: MongoDatabase = mongoClient.getDatabase("smart_farming_db")
  val collection: MongoCollection[Document] = database.getCollection("sensor_data")
  
  implicit val ec: ExecutionContext = ExecutionContext.global

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
    val timestampStr = ZonedDateTime.now(ZoneId.systemDefault())
      .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

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

    Await.result(collection.insertOne(reading).toFuture(), 5.seconds)
  }

  def getLatestReading(): Option[Document] = {
    Await.result(
      collection.find()
        .sort(Document("timestamp" -> -1))
        .first()
        .headOption(),
      5.seconds
    )
  }

  def predict_water(): Future[String] = {
  collection.find()
    .sort(Document("timestamp" -> -1))
    .first()
    .headOption()
    .flatMap {
      case Some(doc) =>
        Future {
          blocking {
            val jsonData = doc.toJson()
            println(s"[DEBUG] Sending to Python:\n$jsonData")

            try {
              val output = (Process(Seq("python", "D:/smart_water/SmartFarmingProject/src/main/script/predict_handler.py")) #<
                new ByteArrayInputStream(jsonData.getBytes("UTF-8"))).!!

              println(s"[DEBUG] Full Python output:\n$output")

              val jsonStart = output.indexOf('{')
              val jsonEnd = output.lastIndexOf('}')
              val jsonStr = if (jsonStart >= 0 && jsonEnd >= 0 && jsonEnd > jsonStart) {
                output.substring(jsonStart, jsonEnd + 1)
              } else {
                s"""{"error": "Could not parse JSON from Python output"}"""
              }

              println(s"[DEBUG] Extracted JSON:\n$jsonStr")
              jsonStr

            } catch {
              case e: Exception =>
                println(s"[ERROR] Python process failed: ${e.getMessage}")
                s"""{"error": "Python prediction failed", "details": "${e.getMessage}"}"""
            }
          }
        }

      case None =>
        Future.successful("""{"error": "No data found"}""")
    }
    .recover {
      case e: Exception =>
        println(s"[ERROR] Prediction pipeline failed: ${e.getMessage}")
        s"""{"error": "Prediction processing failed", "details": "${e.getMessage}"}"""
    }
}



  def readAllDataAsList(): Seq[Document] = {
    Await.result(collection.find().toFuture(), 10.seconds)
  }

  def updateField(timestamp: String, fieldName: String, value: BsonValue): Boolean = {
    Await.result(
      collection.updateOne(
        Filters.equal("timestamp", timestamp),
        Updates.set(fieldName, value)
      ).toFuture(),
      5.seconds
    ).getModifiedCount > 0
  }

  def deleteByTimestamp(timestamp: String): Boolean = {
    Await.result(
      collection.deleteOne(Document("timestamp" -> timestamp)).toFuture(),
      5.seconds
    ).getDeletedCount > 0
  }
}