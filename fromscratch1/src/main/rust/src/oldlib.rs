use slugify::slugify;
use std::time::SystemTime;
use uuid::v1::{Context, Timestamp};
use uuid::Uuid;

#[derive(Clone)]
struct User {
    username: String,
    bio: String,
}

#[derive(Clone)]
struct Article {
    id: Option<Uuid>,
    slug: Option<String>,
    title: String,
    description: String,
    body: String,
    created_at: Option<SystemTime>,
    updated_at: Option<SystemTime>,
    author: Option<String>,
    taglist: Vec<String>,
    favorited: Option<bool>,
    favorites_count: Option<i32>,
}

#[derive(Debug)]
struct Error {
    error: String,
    error_code: i32,
}

impl Article {
    fn for_event(title: &str, description: &str, body: &str, taglist: &Vec<String>) -> Self {
        Self {
            id: None,
            slug: None,
            created_at: None,
            updated_at: None,
            favorited: None,
            favorites_count: None,
            title: title.to_string(),
            description: description.to_string(),
            body: body.to_string(),
            taglist: taglist.clone(),
            author: None,
        }
    }

    fn for_inserting(event_article: &Article, id: Uuid, slug: &str, author: &str) -> Self {
        Self {
            author: Some(author.to_string()),
            id: Some(id),
            slug: Some(slug.to_string()),
            created_at: Some(SystemTime::now()),
            updated_at: Some(SystemTime::now()),
            title: event_article.title.clone(),
            body: event_article.body.clone(),
            description: event_article.description.clone(),
            favorited: Some(false),
            favorites_count: Some(0),
            taglist: event_article.taglist.clone(),
        }
    }
}

fn authenticate_and_get_user(event: &Event, inmemorydb: &InmemoryDB) -> Option<User> {
    for user in &inmemorydb.users {
        if user.username == event.access_token {
            return Some(user.clone());
        }
    }

    None
}

fn generate_id() -> Uuid {
    let context = Context::new(42);
    let ts = Timestamp::from_unix(&context, 1497624119, 1234);
    Uuid::new_v1(ts, &[1, 2, 3, 4, 5, 6]).expect("failed to generate UUID")
}

struct InmemoryDB {
    users: Vec<User>,
    articles: Vec<Article>,
}

struct Event {
    access_token: String,
    article: Article,
}

impl InmemoryDB {
    fn new() -> Self {
        Self {
            users: Vec::new(),
            articles: Vec::new(),
        }
    }

    fn create_article(&mut self, event: Event) -> Result<Article, Error> {
        let authenticated_user = authenticate_and_get_user(&event, self);
        if authenticated_user.is_none() {
            return Err(Error {
                error: String::from("Must be logged in."),
                error_code: 422,
            });
        }

        let article = event.article;
        let id = generate_id();
        let slug = slugify!(&article.title);
        let new_article =
            Article::for_inserting(&article, id, &slug, &authenticated_user.unwrap().username);

        // insert article to inmemorydb
        self.articles.push(new_article.clone());
        Ok(new_article)
    }

    fn empty(&mut self) {
        self.articles = vec![];
    }
}

fn create_article(inmemorydb: &mut InmemoryDB) -> Result<Article, Error> {
    return inmemorydb.create_article(Event {
        access_token: String::from("audacioustux"),
        article: Article::for_event(
            "Test article",
            "Some test article AAAAAAAAAAAAAAAAAAAAA",
            "who's lorem? I don't care about lorem",
            &{
                let taglist: Vec<String> = vec![String::from("Yo"), String::from("Bloom!!!")];
                taglist
            },
        ),
    });
}

pub fn run() {
    let mut imdb = InmemoryDB::new();
    imdb.users.push(User {
        username: String::from("audacioustux"),
        bio: String::from("a tehc enthusiast"),
    });
    create_article(&mut imdb).unwrap();
    imdb.empty();
}
