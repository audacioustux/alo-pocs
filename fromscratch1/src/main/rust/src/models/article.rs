// use chrono::prelude::*;
use chrono::Utc;
use slugify::slugify;
use std::collections::HashSet;
use uuid::{uuid, Uuid};

#[derive(Clone)]
pub struct CreateArticle {
    title: String,
    body: String,
    tags: HashSet<String>,
}
impl CreateArticle {
    pub fn new(title: String, body: String, tags: HashSet<String>) -> Self {
        Self { title, body, tags }
    }
}

pub struct Event {
    pub access_token: String,
    pub request: CreateArticle,
}

#[derive(Clone)]
pub struct Article {
    id: Uuid,
    title: String,
    slug: String,
    body: String,
    author: User,
    tags: HashSet<String>,
    // created_at: DateTime<Utc>,
    // updated_at: Option<DateTime<Utc>>,
}

#[derive(Clone)]
pub struct User {
    id: Uuid,
    username: String,
    bio: String,
}
pub struct InmemoryDB {
    articles: Vec<Article>,
    users: Vec<User>,
}

#[derive(Debug)]
pub struct Error {
    error: String,
    error_code: u16,
}

fn authenticate_and_get_user(token: String, inmemorydb: &InmemoryDB) -> Option<User> {
    for user in &inmemorydb.users {
        if user.username == token {
            return Some(user.clone());
        }
    }

    None
}

impl Article {
    pub fn save(
        author: User,
        title: String,
        body: String,
        tags: HashSet<String>,
        store: &mut InmemoryDB,
    ) -> Self {
        let id = uuid!("67e55044-10b1-426f-9247-bb680e5fe0c8");
        let slug = slugify!(&title);

        let article = Self {
            id,
            title,
            slug,
            body,
            author,
            tags,
            // created_at: Utc::now(),
            // updated_at: None,
        };

        store.articles.push(article.to_owned());
        article
    }
}
impl InmemoryDB {
    pub fn new() -> Self {
        Self {
            articles: vec![],
            users: vec![User {
                id: uuid!("67e55044-10b1-426f-9247-bb680e5fe0c8"),
                username: "audacioustux".to_owned(),
                bio: "a tehc enthusiast".to_owned(),
            }],
        }
    }
    pub fn empty(&mut self) {
        self.articles = vec![]
    }
}

pub fn event_handler(event: Event, store: &mut InmemoryDB) -> Result<Article, Error> {
    let authenticated_user = authenticate_and_get_user(event.access_token, &store);

    if authenticated_user.is_none() {
        return Err(Error {
            error: String::from("Must be logged in."),
            error_code: 422,
        });
    }

    let CreateArticle { title, body, tags } = event.request;
    Ok(Article::save(
        authenticated_user.unwrap(),
        title,
        body,
        tags,
        store,
    ))
}
