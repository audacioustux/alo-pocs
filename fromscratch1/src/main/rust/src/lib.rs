// mod utils;

use js_sys::Math;
use wasm_bindgen::prelude::*;

// When the `wee_alloc` feature is enabled, use `wee_alloc` as the global
// allocator.
#[cfg(feature = "wee_alloc")]
#[global_allocator]
static ALLOC: wee_alloc::WeeAlloc = wee_alloc::WeeAlloc::INIT;

#[wasm_bindgen]
#[repr(u8)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Cell {
    Dead = 0,
    Alive = 1,
}

#[wasm_bindgen]
pub struct Universe {
    width: u32,
    height: u32,
    cells: Vec<Cell>,
}

/// Public methods, exported to JavaScript.
#[wasm_bindgen]
impl Universe {
    pub fn new() -> Universe {
        let width = 64 as u32;
        let height = 64 as u32;

        Universe {
            width,
            height,
            cells: vec![Cell::Dead; (width * height) as usize],
        }
    }

    pub fn add_glider(&mut self) {
        // Glider or Featherweight spaceship:
        // https://conwaylife.com/wiki/Glider
        //       1
        // 1     1
        //    1  1
        self.set_cell(0, 2, Cell::Alive);

        self.set_cell(1, 0, Cell::Alive);
        self.set_cell(1, 2, Cell::Alive);

        self.set_cell(2, 1, Cell::Alive);
        self.set_cell(2, 2, Cell::Alive);
    }

    pub fn kill_all(&mut self) {
        self.cells = vec![Cell::Dead; (self.width() * self.height()) as usize];
    }

    pub fn reset_random(&mut self) {
        self.cells = (0..self.width() * self.height())
            .map(|_i| {
                if Math::random() >= 0.5 {
                    Cell::Dead
                } else {
                    Cell::Alive
                }
            })
            .collect();
    }

    pub fn reset_mod2_mod7(&mut self) {
        self.cells = (0..self.width() * self.height())
            .map(|i| {
                if i % 2 == 0 || i % 7 == 0 {
                    Cell::Alive
                } else {
                    Cell::Dead
                }
            })
            .collect();
    }

    pub fn width(&self) -> u32 {
        self.width
    }

    pub fn height(&self) -> u32 {
        self.height
    }

    // This returns and unsafe pointer!
    pub fn cells(&self) -> *const Cell {
        self.cells.as_ptr()
    }

    pub fn tick(&mut self) {
        let mut next = self.cells.clone();

        for row in 0..self.height {
            for col in 0..self.width {
                let idx = self.get_index(row, col);
                let cell = self.cells[idx];
                let live_neighbors = self.live_neighbor_count(row, col);

                let next_cell = match (cell, live_neighbors) {
                    // Rule 1: Any live cell with fewer than two live neighbours
                    // dies, as if caused by underpopulation.
                    (Cell::Alive, x) if x < 2 => Cell::Dead,
                    // Rule 2: Any live cell with two or three live neighbours
                    // lives on to the next generation.
                    (Cell::Alive, 2) | (Cell::Alive, 3) => Cell::Alive,
                    // Rule 3: Any live cell with more than three live
                    // neighbours dies, as if by overpopulation.
                    (Cell::Alive, x) if x > 3 => Cell::Dead,
                    // Rule 4: Any dead cell with exactly three live neighbours
                    // becomes a live cell, as if by reproduction.
                    (Cell::Dead, 3) => Cell::Alive,
                    // All other cells remain in the same state.
                    (otherwise, _) => otherwise,
                };

                next[idx] = next_cell;
            }
        }

        self.cells = next;
    }

    // Given a zero based row and column coordinates, a linear index into the
    // cells vector is returned.
    fn get_index(&self, row: u32, column: u32) -> usize {
        (row * self.width + column) as usize
    }

    // Sets a cell value for the given zero based row and column.
    fn set_cell(&mut self, row: u32, column: u32, new_val: Cell) {
        let idx = self.get_index(row, column);
        self.cells[idx] = new_val;
    }

    fn live_neighbor_count(&self, row: u32, column: u32) -> u8 {
        let mut count = 0;
        for delta_row in [self.height - 1, 0, 1].iter().cloned() {
            for delta_col in [self.width - 1, 0, 1].iter().cloned() {
                if delta_row == 0 && delta_col == 0 {
                    continue;
                }

                let neighbor_row = (row + delta_row) % self.height;
                let neighbor_col = (column + delta_col) % self.width;
                let idx = self.get_index(neighbor_row, neighbor_col);
                count += self.cells[idx] as u8;
            }
        }
        count
    }
}
