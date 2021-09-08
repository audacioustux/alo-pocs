// https://github.com/anishkny/realworld-dynamodb-lambda/tree/master/src
// import slugify from "./slugify";

type User = { username: string; bio: string; following?: boolean };
type Article = {
  id?: string;
  // slug?: string;
  createdAt?: number;
  updatedAt?: number;
  author?: User | User["username"];
  favorited?: boolean;
  favoritesCount?: number;
  title: string;
  description: string;
  body: string;
  tagList: Array<string>;
};

let inmemorydb: {
  users: Array<User>;
  articles: Array<Article>;
} = {
  users: [{ username: "audacioustux", bio: "a tehc enthusiast" }],
  articles: [],
};

type Event = {
  authtoken: string;
  body: {
    article: Article;
  };
};
function authenticateAndGetUser(event: Event): User {
  return inmemorydb.users[
    inmemorydb.users.findIndex(({ username }) => username === event.authtoken)
  ];
}

type Error = { error: string; errorCode: Number };
function create(event: Event) {
  const authenticatedUser = authenticateAndGetUser(event);
  if (!authenticatedUser) {
    return { error: "Must be logged in.", errorCode: 422 };
  }

  const {
    article,
    article: { title, description, body, tagList },
  } = event.body;
  if (!article) {
    return { error: "Article must be specified.", errorCode: 422 };
  }
  ["title", "description", "body"].forEach(
    (expectedField: keyof Article): Error | void => {
      if (!article[expectedField]) {
        return { error: `${expectedField} must be specified.`, errorCode: 422 };
      }
    }
  );

  const id = ((Math.random() * Math.pow(36, 6)) | 0).toString(36);
  const timestamp = Date.now();
  // const slug = slugify(title) + " - " + id;

  let _article: Article = {
    id,
    // slug,
    title,
    description,
    body,
    createdAt: timestamp,
    updatedAt: timestamp,
    author: authenticatedUser.username,
    tagList: tagList
      ? tagList.filter(function (x, i, a) {
          return a.indexOf(x) == i;
        })
      : [],
  };
  inmemorydb.articles.push(_article);

  _article.favorited = false;
  _article.favoritesCount = 0;
  _article.author = {
    username: authenticatedUser.username,
    bio: authenticatedUser.bio || "",
    following: false,
  };

  return { article: _article };
}

function createArticle() {
  return create({
    authtoken: "audacioustux",
    body: {
      article: {
        title: "testing blabla",
        description: "testing bloom phew phew",
        body: "lorem *ipsum* sit amet dolor am jam kathal",
        tagList: ["test", "test", "bloom", "lorem-ipsum"],
      },
    },
  });
}

export function run() {
  createArticle();
  inmemorydb.articles = [];
}
