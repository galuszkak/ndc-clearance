import hljs from "highlight.js/lib/core";
import xml from "highlight.js/lib/languages/xml";
import "highlight.js/styles/github-dark.css";

hljs.registerLanguage("xml", xml);

export { hljs };
