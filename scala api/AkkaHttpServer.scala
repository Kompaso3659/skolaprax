import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, Materializer}
import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{Failure, Success}

object AkkaHttpServer extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val nasaApiClient = new NasaApiClient()

  val route = {
    concat(
      path("hello_to") {
        get {
          parameters("name".as[String]) { name =>
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"Hello to $name"))
          }
        }
      },
      path("hello") {
        get {
          onComplete(Future {
            "ourString"
          }) {
            case Success(value) => complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Hello to everyone"))
            case Failure(exception) => complete(InternalServerError, s"An error occurred: ${exception.getMessage}")
          }
        }
      },
      path("nasa") {
        get {
          parameters("date".as[String]) { date =>
            onComplete(nasaApiClient.getImageOfTheDay(date)) {
              case Success(response) =>
                if (response.media_type == "image") {
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                    s"""
                       |<h2>${response.title}</h2>
                       |<p>${response.explanation}</p>
                       |<img src="${response.url}"/>
                       |""".stripMargin))
                } else if (response.media_type == "video") {
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                    s"""
                       |<h2>${response.title}</h2>
                       |<p>${response.explanation}</p>
                       |<iframe width="560" height="315" src="${response.url}" frameborder="0" allowfullscreen></iframe>
                       |""".stripMargin))
                } else {
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Unknown media type"))
                }
              case Failure(ex) =>
                complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                  s"Failed to retrieve NASA data: ${ex.getMessage}"))
            }
          }
        }
      },
      pathEndOrSingleSlash {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Response from server"))
        }
      },
      pathPrefix("user" / LongNumber)(userId =>
        concat(
          pathEndOrSingleSlash {
            get {
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"user detail of user $userId"))
            }
          },
          path("delete") {
            decodeRequest {
              post {
                entity(as[String]) { ent: String =>
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"deleting user $userId with params $ent"))
                }
              }
            }
          }
        )
      )
    )
  }

  val bindingFut = for {
    binding <- Http().newServerAt("localhost", 8080).bind(route)
    _ = println(s"Server running on ${binding.localAddress.getHostName}:${binding.localAddress.getPort}")
  } yield binding

  StdIn.readLine()
  bindingFut.flatMap(_.unbind()).andThen(_ => system.terminate())
}
