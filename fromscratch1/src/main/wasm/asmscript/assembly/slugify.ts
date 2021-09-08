import charMap from "./charMap";
import locales from "./locales";

function replace(
  string: string,
  options?:
    | {
        replacement?: string;
        remove?: RegExp;
        lower?: boolean;
        strict?: boolean;
        locale?: keyof typeof locales;
        trim?: boolean;
      }
    | string
): string {
  if (typeof string !== "string") {
    throw new Error("slugify: string argument expected");
  }

  options =
    typeof options === "string" ? { replacement: options } : options || {};

  var locale = (options.locale && locales[options.locale]) || {};

  var replacement =
    options.replacement === undefined ? "-" : options.replacement;

  var trim = options.trim === undefined ? true : options.trim;

  var slug = string
    .normalize()
    .split("")
    // replace characters based on charMap
    .reduce(function (result, ch) {
      return (
        result +
        (locale[ch] || charMap[ch] || (ch === replacement ? " " : ch))
          // remove not allowed characters
          .replace(options.remove || /[^\w\s$*_+~.()'"!\-:@]+/g, "")
      );
    }, "");

  if (options.strict) {
    slug = slug.replace(/[^A-Za-z0-9\s]/g, "");
  }

  if (trim) {
    slug = slug.trim();
  }

  // Replace spaces with replacement character, treating multiple consecutive
  // spaces as a single space.
  slug = slug.replace(/\s+/g, replacement);

  if (options.lower) {
    slug = slug.toLowerCase();
  }

  return slug;
}

replace.extend = function (customMap) {
  Object.assign(charMap, customMap);
};

export default replace;
