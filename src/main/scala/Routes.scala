import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import org.mongodb.scala.bson.{BsonValue, BsonDouble, BsonInt32}
import org.mongodb.scala.Document

object Routes {

  // Case class matching your sensor data
  case class SensorData(
    soilMoisture: Double,
    temperature: Double,
    airHumidity: Double,
    ph: Double,
    rainfall: Double,
    n: Int,
    p: Int,
    k: Int
  )

  val routes: Route =
    concat(

      // Serve index.html
      pathSingleSlash {
        getFromFile("public/index.html")
      },

      // Serve static files like JS, CSS
      getFromDirectory("public"),

      // Insert new reading (manual input)
      path("insert") {
        post {
          entity(as[SensorData]) { data =>
            SmartFarmingApp.insertReading(
              data.soilMoisture, data.temperature, data.airHumidity,
              data.ph, data.rainfall, data.n, data.p, data.k
            )
            complete(StatusCodes.OK, "Inserted successfully")
          }
        }
      },

      // Get all data
      path("all") {
        get {
          val docs = SmartFarmingApp.readAllDataAsList()
          complete(docs.map(_.toJson()))
        }
      },

      // Get latest data
      path("latest") {
        get {
          SmartFarmingApp.getLatestReading() match {
            case Some(doc) => complete(doc.toJson())
            case None => complete(StatusCodes.NotFound -> "No data found")
          }
        }
      },

      // Update by timestamp
      path("update") {
        put {
          parameters("timestamp", "field", "value") { (ts, field, valueStr) =>
            val bsonValue: Option[BsonValue] = field.toLowerCase match {
              case "soil_moisture" | "temperature" | "air_humidity" | "ph" | "rainfall" =>
                try Some(BsonDouble(valueStr.toDouble)) catch { case _: NumberFormatException => None }
              case "n" | "p" | "k" =>
                try Some(BsonInt32(valueStr.toInt)) catch { case _: NumberFormatException => None }
              case _ => None
            }

            bsonValue match {
              case Some(v) =>
                val success = SmartFarmingApp.updateField(ts, field, v)
                if (success) complete(StatusCodes.OK, s"Field '$field' updated.")
                else complete(StatusCodes.NotFound, "Timestamp not found.")
              case None =>
                complete(StatusCodes.BadRequest, "Invalid field or value type.")
            }
          }
        }
      },

      // Delete by timestamp
      path("delete") {
        delete {
          parameter("timestamp".as[String]) { ts =>
            val success = SmartFarmingApp.deleteByTimestamp(ts)

            if (success) complete(StatusCodes.OK, "Deleted successfully")
            else complete(StatusCodes.NotFound, "Timestamp not found")
          }
        }
      }

      path("predict-water") {
  get {
    val result = SmartFarmingApp.getAndPredict()
    complete(result)
  }
}
    )
}
