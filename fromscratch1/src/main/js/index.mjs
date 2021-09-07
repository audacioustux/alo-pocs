import { run } from "./realworld.mjs";

for (let n = 10; n--; ) {
  console.time(`bench${n}`);
  for (let i = 10000000; i--; ) run();
  console.timeEnd(`bench${n}`);
}
