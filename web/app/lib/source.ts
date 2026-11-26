import { loader, type InferPageType } from "fumadocs-core/source";
import { lucideIconsPlugin } from "fumadocs-core/source/lucide-icons";
import { defineDocs } from "fumadocs-mdx/macro";

export const docs = defineDocs({
  dir: "content/docs",
  docs: {
    async: true,
    postprocess: {
      includeProcessedMarkdown: true,
    },
  },
});

export const source = loader({
  source: docs.toFumadocsSource(),
  baseUrl: "/docs",
  plugins: [lucideIconsPlugin()],
});

export function getDocsPage(path: string) {
  const page = docs.getPage(path);
  if (!page) throw new Error(`unknown page: ${path}`);
  return page;
}

export function getPageMarkdownUrl(page: InferPageType<typeof source>) {
  const segments = [...page.slugs, "content.md"];

  return {
    segments,
    url: `/llms.mdx/docs/${segments.join("/")}`,
  };
}

export async function getLLMText(page: InferPageType<typeof source>) {
  const processed = await getDocsPage(page.path).getText("processed");

  return `# ${page.data.title} (${page.url})

${processed}`;
}
