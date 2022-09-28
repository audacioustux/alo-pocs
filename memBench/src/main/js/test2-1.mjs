const id = Math.random()
export function todos_create() {
    let result = {};
    let todo = { id, text: "test", completed: false };

    return JSON.stringify(Object.assign(result, todo));
}
