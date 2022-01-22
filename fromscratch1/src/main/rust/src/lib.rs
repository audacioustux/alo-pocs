mod models;

use std::collections::HashSet;
use std::iter::FromIterator;

use crate::models::article::{event_handler, CreateArticle, Event, InmemoryDB};

#[no_mangle]
pub extern "C" fn run() {
    let mut store = InmemoryDB::new();

    event_handler(
        Event {
            access_token: "audacioustux".to_owned(),
            request: CreateArticle::new(
                "test article".to_owned(),
                "some test article".to_owned(),
                HashSet::from_iter(vec!["test".to_owned(), "bloom".to_owned()]),
            ),
        },
        &mut store,
    )
    .unwrap();

    println!(".");
    store.empty();
}
