use std::time::SystemTime;
use slugify::slugify;

#[derive(Clone)]
struct User {
    pub username: String,
    pub bio: String
}

#[derive(Clone)]
struct Article {
    pub id: Option<i32>,
    pub slug: Option<String>,
    pub title: String,
    pub description: String,
    pub body: String,
    pub created_at: Option<SystemTime>,
    pub updated_at: Option<SystemTime>,
    pub author: Option<String>,
    pub taglist: Vec<String>,
    pub favorited: Option<bool>,
    pub favorites_count: Option<i32>
}

#[derive(Debug)]
struct Error {
    pub error: String,
    pub error_code: i32
}

impl Article {
    pub fn for_event(
            title: &str,
            description: &str,
            body: &str,
            taglist: &Vec<String>
        ) -> Self {
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
            author: None
        }
    }

    pub fn for_inserting(
        event_article: &Article,
        id: i32,
        slug: &str,
        author: &str
    ) -> Self {
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
            taglist: event_article.taglist.clone() 
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

fn generate_id() -> i32 {
    rand::random()
}

struct InmemoryDB {
    users: Vec<User>,
    articles: Vec<Article>
}
    
struct Event {
    pub access_token: String,
    pub article: Article
}

impl InmemoryDB {
    pub fn new() -> Self {
        Self {
            users: Vec::new(),
            articles: Vec::new()
        }
    }

    pub fn create_article(&mut self, event: Event) -> Result<Article, Error> {
        let authenticated_user = authenticate_and_get_user(&event, self);
        if authenticated_user.is_none() {
            return Err(Error {
                error: String::from("Must be logged in."),
                error_code: 422
            })
        }
        
        let article = event.article;
        let id = generate_id();
        let slug = slugify!(&article.title);
        let new_article = Article::for_inserting(&article, id, &slug, &authenticated_user.unwrap().username);

        // insert article to inmemorydb
        self.articles.push(new_article.clone());
        Ok(new_article)
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
            }
        )
    })
}

#[no_mangle]
pub extern "C" fn run() {
    let mut imdb = InmemoryDB::new();
    imdb.users.push(User {
        username: String::from("audacioustux"),
        bio: String::from("a tehc enthusiast")
    });
    create_article(&mut imdb).unwrap();
}