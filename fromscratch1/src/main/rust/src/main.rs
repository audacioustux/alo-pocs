mod lib;

fn main() {
    use chrono::Utc;

    let args: Vec<String> = std::env::args().collect();

    if let Some(_) = args.get(1) {
        println!("warmup...");
        let start = Utc::now().time();
        for _ in 1..=5000000 {
            crate::lib::run();
        }
        let duration = Utc::now().time() - start;
        println!("\t\t{:?}", duration);
    }

    println!("bench...");
    let start = Utc::now().time();
    for _ in 1..=5000000 {
        crate::lib::run();
    }
    let duration = Utc::now().time() - start;
    println!("\t\t{:?}", duration);
}
