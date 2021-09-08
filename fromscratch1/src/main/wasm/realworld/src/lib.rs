#[no_mangle]
pub extern "C" fn floyd() {
    let mut _number = 1;
    let rows = 10;
    for i in 1..=rows {
        for _ in 1..=i {
            _number += 1;
        }
    }
}
