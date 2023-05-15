import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import slick.jdbc.H2Profile.api._
import com.example.User

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success}

object Main extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val timeout: Timeout = Timeout(5.seconds)

  case class User(name: String, age: Int)

  object User {
    def unapply(user: User): Option[(String, Int)] = Some(user.name, user.age)
  }

  case class User(id: Long, name: String)

  class Users(tag: Tag) extends Table[User](tag, "USERS") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def name = column[String]("NAME")
    def * = (id, name) <> (User.tupled, User.unapply)
  }

  val users = TableQuery[Users]

  val db = Database.forConfig("h2mem1")
  val createTableAction = users.schema.create
  val createTableFuture = db.run(createTableAction)
  createTableFuture.onComplete {
    case Success(_) => println("Users table created successfully")
    case Failure(ex) => println(s"Failed to create users table: ${ex.getMessage}")
  }

  case class NasaApiResponse(date: String, explanation: String, media_type: String, title: String, url: String)

  class NasaApiClient(apiKey: String = "")(implicit system: ActorSystem, mat: Materializer) {
    private val apiBaseUrl = "https://api.nasa.gov/planetary/apod"

    def getImageOfTheDay(date: String): Future[NasaApiResponse] = {
      val requestUrl = s"$apiBaseUrl?date=$date&api_key=$apiKey"
      val request = HttpRequest(uri = requestUrl)
      val responseFuture = Http().singleRequest(request)
      responseFuture.flatMap { response =>
        response.status match {
          case StatusCodes.OK =>
            response.entity.toStrict(5.seconds).map { entity =>
              val json = entity.data.utf8String.parseJson
              json.convertTo[NasaApiResponse]
            }
          case _ =>
            response.discardEntityBytes()
            Future.failed(new RuntimeException(s"Unexpected status code ${response.status}"))
        }
      }
    }
  }

  val apiKey = "LSuFph5M85xV8HzueGPdzjU1RKWGWzx0ItC3LyJP"
  val nasaApiClient = new NasaApiClient(apiKey)

  val route =
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
          onComplete(Future.successful("ourString")) {
            case Success(value) =>
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Hello to everyone"))
            case Failure(exception) =>
              complete(StatusCodes.InternalServerError, s"An error occurred: ${exception.getMessage}")
          }
        }
      },
      path("nasa") {
        get {
          parameters("date".as[String]) { date =>
            onComplete(nasaApiClient.getImageOfTheDay(date)) {
              case Success(response) =>
                if (response.media_type == "image") {
                  complete(
                    HttpResponse(
                      StatusCodes.OK,
                      entityHttpEntity(
                        ContentTypes.`text/html(UTF-8)`,
                        s"""
                           |<h2>${response.title}</h2>
                           |<p>${response.explanation}</p>
                           |<img src="${response.url}"/>
                           |""".stripMargin
                      )
                    )
                  )
                } else if (response.media_type == "video") {
                  complete(
                    HttpResponse(
                      StatusCodes.OK,
                      entity = HttpEntity(
                        ContentTypes.`text/html(UTF-8)`,
                        s"""
                           |<h2>${response.title}</h2>
                           |<p>${response.explanation}</p>
                           |<iframe width="560" height="315" src="${response.url}" frameborder="0" allowfullscreen></iframe>
                           |""".stripMargin
                      )
                    )
                  )
                } else {
                  complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, "Unknown media type")))
                }
              case Failure(ex) =>
                complete(
                  HttpResponse(
                    StatusCodes.InternalServerError,
                    entity = HttpEntity(
                      ContentTypes.`text/html(UTF-8)`,
                      s"Failed to retrieve NASA data: ${ex.getMessage}"
                    )
                  )
                )
            }
          }
        }
      },
      pathPrefix("user" / LongNumber) { userId =>
        concat(
          pathEndOrSingleSlash {
            get {
              val userQuery = users.filter(_.id === userId)
              val userFuture = db.run(userQuery.result.headOption)
              onComplete(userFuture) {
                case Success(Some(user)) =>
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"user detail of user ${user.id}: ${user.name}"))
                case Success(None) =>
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "User not found"))
                case Failure(ex) =>
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"An error occurred: ${ex.getMessage}"))
              }
            }
          },
          path("delete") {
            post {
              decodeRequest {
                entity(as[String]) { ent: String =>
                  val userQuery = users.filter(_.id === userId)
                  val deleteAction = userQuery.delete
                  val deleteFuture = db.run(deleteAction)
                  onComplete(deleteFuture) {
                    case Success(_) =>
                      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"Deleting user $userId with params $ent"))
                    case Failure(ex) =>
                      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"An error occurred: ${ex.getMessage}"))
                  }
                }
              }
            }
          }
        )
      },
      pathEndOrSingleSlash {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Response from server"))
        }
      }
    )

  val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)
  bindingFuture.onComplete {
    case Success(binding) =>
      println(s"Server online at http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}/")
    case Failure(ex) =>
      println(s"Server could not start: ${ex.getMessage}")
  }

  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
