use std::collections::{HashMap, HashSet};

use chrono::prelude::*;

use slugify::slugify;
use uuid::Uuid;

#[derive(Default)]
struct Article {
    id: Option<Uuid>,
    pub title: String,
    slug: String,
    body: String,
    author: Uuid,
    created_at: Option<DateTime<Utc>>,
    updated_at: Option<DateTime<Utc>>,
    taglist: HashSet<String>,
}

struct InmemoryDB {
    articles: Vec<Article>,
}

struct Error {
    error: String,
    error_code: u16,
}
