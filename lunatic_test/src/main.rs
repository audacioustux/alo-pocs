use std::time::Duration;

use lunatic::{sleep, Mailbox, Process};

pub fn main() {
    let childs = (1..=5_000).map(|_| {
        Process::spawn((), |_capture, _mailbox: Mailbox<i32>| {
            sleep(Duration::from_secs(5));
            // dbg!(".");
        })
    });

    childs.into_iter().for_each(|child| child.send(1));

    sleep(Duration::from_secs(8))
}
