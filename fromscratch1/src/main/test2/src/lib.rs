use std::ffi::{c_char, CString};

use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize)]
struct Todo {
    id: u32,
    text: String,
    completed: bool,
}

#[no_mangle]
pub unsafe extern "C" fn todos_create() -> *const c_char {
    let todo = Todo {
        id: u32::MAX,
        text: "test".to_string(),
        completed: false,
    };

    CString::new(serde_json::to_string(&todo).unwrap())
        .unwrap()
        .into_raw()
}
