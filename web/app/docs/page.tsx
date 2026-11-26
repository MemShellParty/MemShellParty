import type { Route } from "./+types/page";

import { useFumadocsLoader } from "fumadocs-core/source/client";
import { DocsLayout } from "fumadocs-ui/layouts/docs";
import {
  DocsBody,
  DocsDescription,
  DocsPage,
  DocsTitle,
  MarkdownCopyButton,
  ViewOptionsPopover,
} from "fumadocs-ui/layouts/docs/page";
import { use } from "react";
import { useLocation } from "react-router";

import { useMDXComponents } from "@/components/mdx";
import { baseOptions } from "@/lib/layout.shared";
import { getDocsPage, getPageMarkdownUrl, source } from "@/lib/source";
import { preserveQuery } from "@/utils/preserve-query";

export async function loader({ params }: Route.LoaderArgs) {
  const slugs = params["*"].split("/").filter((v) => v.length > 0);
  const page = source.getPage(slugs);
  if (!page) throw new Response("Not found", { status: 404 });

  const [pageTree] = await Promise.all([
    source.serializePageTree(source.getPageTree()),
    getDocsPage(page.path).preload(),
  ]);

  return {
    path: page.path,
    markdownUrl: getPageMarkdownUrl(page).url,
    pageTree,
  };
}

function Content({ path, markdownUrl }: { path: string; markdownUrl: string }) {
  const location = useLocation();
  const page = getDocsPage(path);
  const authenticatedMarkdownUrl = preserveQuery(markdownUrl, location);

  // content is loaded lazily; preload() in the loader avoids suspending
  const { toc } = use(page.load());
  const Mdx = page.body;

  return (
    <DocsPage toc={toc} tableOfContent={{ style: "clerk" }}>
      <title>{page.title}</title>
      <meta name="description" content={page.description} />
      <DocsTitle>{page.title}</DocsTitle>
      <DocsDescription>{page.description}</DocsDescription>
      <div className="-mt-4 flex flex-row items-center gap-2 border-b pb-6">
        <MarkdownCopyButton markdownUrl={authenticatedMarkdownUrl} />
        <ViewOptionsPopover
          markdownUrl={authenticatedMarkdownUrl}
          githubUrl={`https://github.com/MemShellParty/MemShellParty/blob/master/web/content/docs/${path}`}
        />
      </div>
      <DocsBody>
        <Mdx components={useMDXComponents()} />
      </DocsBody>
    </DocsPage>
  );
}

export default function Page({ loaderData }: Route.ComponentProps) {
  const { pageTree, path, markdownUrl } = useFumadocsLoader(loaderData);

  return (
    <DocsLayout {...baseOptions()} tree={pageTree}>
      <Content path={path} markdownUrl={markdownUrl} />
    </DocsLayout>
  );
}
