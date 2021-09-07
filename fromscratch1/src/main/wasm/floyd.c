#include <emscripten.h>

void EMSCRIPTEN_KEEPALIVE floyd() {
  int number = 1;
  int rows = 10;
  for (int i = 1; i <= rows; i++) {
    for (int j = 1; j <= i; j++) {
      ++number;
    }
  }
}
