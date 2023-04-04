import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future
import scala.io.StdIn

object NasaAPIDemo {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    import system.dispatcher

    val apiKey = "LSuFph5M85xV8HzueGPdzjU1RKWGWzx0ItC3LyJP"
    val date = StdIn.readLine("Enter date (YYYY-MM-DD): ")
    val apiUrl = s"https://api.nasa.gov/planetary/apod?api_key=$apiKey&date=$date"

    val headers = List.empty
    val request = HttpRequest(uri = Uri(apiUrl), headers = headers)

    val responseFuture: Future[NasaApiResponse] =
      Source.single(request)
        .via(Http().outgoingConnectionHttps("api.nasa.gov"))
        .flatMapConcat { response =>
          response.entity.dataBytes
            .runFold(ByteString.empty)(_ ++ _)
            .map(data => (response, data))
        }
        .mapAsync(1) { case (response, data) =>
          Unmarshal(data).to[NasaApiResponse].map(result => (response, result))
        }
        .runWith(Sink.head)

    responseFuture.onComplete {
      case scala.util.Success((response, nasaApiResponse)) =>
        if (nasaApiResponse.media_type == "image") {
          println(s"Title: ${nasaApiResponse.title}")
          println(s"Explanation: ${nasaApiResponse.explanation}")
          println(s"Image URL: ${nasaApiResponse.url}")
        } else if (nasaApiResponse.media_type == "video") {
          println(s"Title: ${nasaApiResponse.title}")
          println(s"Explanation: ${nasaApiResponse.explanation}")
          println(s"Video URL: ${nasaApiResponse.url}")
        } else {
          println("Unknown media type")
        }
      case scala.util.Failure(ex) =>
        println(s"Failed to retrieve NASA data: ${ex.getMessage}")
    }
  }
}

case class NasaApiResponse(title: String, explanation: String, media_type: String, url: String)