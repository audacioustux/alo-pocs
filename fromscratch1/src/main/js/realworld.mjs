import slugify from "./slugify.mjs";

let inmemorydb = {
  users: [{ username: "audacioustux", bio: "a tehc enthusiast" }],
  articles: [],
};

function authenticateAndGetUser(event) {
  return inmemorydb.users.find(({ username }) => username === event.authtoken);
}

function create(event) {
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
  for (const expectedField of ["title", "description", "body"]) {
    if (!article[expectedField]) {
      return { error: `${expectedField} must be specified.`, errorCode: 422 };
    }
  }

  const id = ((Math.random() * Math.pow(36, 6)) | 0).toString(36);
  const timestamp = new Date().getTime();
  const slug = slugify(title) + " - " + id;

  let _article = {
    id,
    slug,
    title,
    description,
    body,
    createdAt: timestamp,
    updatedAt: timestamp,
    author: authenticatedUser.username,
  };
  _article.tagList = tagList ? Array.from(new Set(tagList)) : "";

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

export function createArticle() {
  return JSON.stringify(
    create({
      authtoken: "audacioustux",
      body: {
        article: {
          title: "testing blabla",
          description: "testing bloom phew phew",
          body: "lorem *ipsum* sit amet dolor am jam kathal",
          tagList: ["test", "test", "bloom", "lorem-ipsum"],
        },
      },
    })
  );
}
