use std::time::Duration;

use lunatic::{sleep, Mailbox, Process};

pub fn main() {
    let childs = (1..=5_000).map(|n| {
        Process::spawn(n, |_capture, _mailbox: Mailbox<i32>| {
            // sleep(Duration::from_secs(5));
            loop {
                // println!("{}", capture.to_string());
            }
            // dbg!(".");
        })
    });

    childs.into_iter().for_each(|child| child.send(1));

    sleep(Duration::from_secs(30))
}
