export function floyd() {
  let number = 1;
  const rows = 10;
  for (let i = 1; i <= rows; i++) {
    for (let j = 1; j <= i; j++) {
      ++number;
    }
  }
}
