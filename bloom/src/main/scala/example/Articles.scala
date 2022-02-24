package example

import scala.collection.mutable
import java.util.Random
import io.jvm.uuid._

object Slug {
  def apply(input: String): String = slugify(input)

  def slugify(input: String): String = {
    import java.text.Normalizer
    Normalizer
      .normalize(input, Normalizer.Form.NFD)
      .replaceAll(
        "[^\\w\\s-]",
        ""
      ) // Remove all non-word, non-space or non-dash characters
      .replace('-', ' ') // Replace dashes with spaces
      .trim // Trim leading/trailing whitespace (including what used to be leading/trailing dashes)
      .replaceAll(
        "\\s+",
        "-"
      ) // Replace whitespace (including newlines and repetitions) with single dashes
      .toLowerCase // Lowercase the final results
  }
}

case class Error(msg: String, code: Int)
case class Event(authToken: Option[String], body: Option[Article])

case class User(username: String, bio: String)
object User {
  var users: mutable.Seq[User] =
    mutable.Seq(User("audacioustux", "henlo meh tangim"))

  def authenticateAndGetUser(event: Event): Option[User] = {
    event.authToken.flatMap(authToken =>
      users.find(user => user.username == authToken)
    )
  }
}
final case class Article(
    title: String,
    body: String,
    description: String,
    id: Option[String] = None,
    slug: Option[String] = None,
    createdAt: Option[Long] = None,
    updatedAt: Option[Long] = None,
    author: Option[User] = None,
    tagList: Option[Set[String]] = None,
    favoritesCount: Option[Int] = None,
    favorite: Option[Boolean] = None
)

class Articles {
  var articles: mutable.Seq[Article] = mutable.Seq()

  def create(event: Event): Error | Article = {
    val authenticatedUser = User.authenticateAndGetUser(event) match {
      case Some(authenticatedUser) => authenticatedUser
      case None                    => return Error("must be logged in", 422)
    }
    var article = event.body match {
      case Some(article) => article
      case None          => return Error("article must be specified", 422)
    }

    val timestamp = System.currentTimeMillis()
    article = article.copy(
      id = Some(UUID.random.string),
      slug = Some(Slug(article.title)),
      createdAt = Some(timestamp),
      author = Some(authenticatedUser),
      favorite = Some(false),
      favoritesCount = Some(2)
    )
    articles :+= article

    article
  }
  def reset(): Unit = articles = mutable.Seq()

  def run(): Unit =
    create(
      Event(
        Some("audacioustux"),
        Some(
          Article(
            "testing blabla",
            "testing bloom phew phew",
            "lorem *ipsum* sit amet dolor am jam kathal",
            tagList = Some(Set("test", "test", "bloom", "lorem-ipsum"))
          )
        )
      )
    )
    reset()
}

//given Runnable[Articles] with
//  extension (a: Articles)
//    def run: Unit =
//      a.create(
//        Event(
//          Some("audacioustux"),
//          Some(
//            Article(
//              "Test article",
//              "Some test article AAAAAAAAAAAAAAAAAAAAA",
//              "who's lorem? I don't care about lorem"
//            )
//          )
//        )
//      )
//      a.reset()
