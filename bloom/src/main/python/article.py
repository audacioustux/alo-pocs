import site
from datetime import datetime
import random

from slugify import slugify

inmemorydb = {
  "users": [{ "username": "audacioustux", "bio": "a tehc enthusiast" }],
  "articles": [],
}

def authenticateAndGetUser(event):
    if inmemorydb["users"][0]["username"] == event["authtoken"]:
        return inmemorydb["users"][0]

def create(event):
    authenticatedUser = authenticateAndGetUser(event)
    if not authenticatedUser:
        return {"error": "Must be logged in.", "errorCode": 422}

    article = event["body"]["article"]
    title = slugify(article["title"])
    description = article["description"]
    body = article["body"]
    tagList = article["tagList"]

    if not article:
        return { "error": "Article must be specified.", "errorCode": 422 }
    for expectedField in ["title", "description", "body"]:
        if not article[expectedField]:
            return { "error": "${expectedField} must be specified.", "errorCode": 422 }


    id = str(abs(random.random() * pow(36, 6)));
    timestamp = datetime.now();
    slug = title + " - " + id;

    _article = {
      "id":id,
      "slug": slug,
      "title": title,
      "description": description,
      "body": body,
      "createdAt": timestamp,
      "updatedAt": timestamp,
      "author": authenticatedUser["username"],
      "tagList": set([])
    }
    if tagList:
        _article["tagList"] = set(tagList)
    

    inmemorydb["articles"].append(_article)

    _article["favorited"] = False;
    _article["favoritesCount"] = 0;
    _article["author"] = {
      "username": authenticatedUser["username"],
      "bio": authenticatedUser["bio"] or "",
      "following": False,
    };

    return _article;


def createArticle() :
  return create({
        "authtoken": "audacioustux",
        "body": {
          "article": {
            "title": "testing blabla",
            "description": "testing bloom phew phew",
            "body": "lorem *ipsum* sit amet dolor am jam kathal",
            "tagList": ["test", "test", "bloom", "lorem-ipsum"],
          },
        },
    })

def run():
  createArticle();
  inmemorydb["articles"] = [];

run
