import type { MDXComponents } from "mdx/types";

import { Image, type ImageProps } from "fumadocs-core/framework";
import defaultMdxComponents from "fumadocs-ui/mdx";

import { Mermaid } from "@/components/mdx/mermaid";

function DocImage(props: ImageProps) {
  return (
    <Image
      loading="lazy"
      sizes="(max-width: 768px) 100vw, (max-width: 1200px) 70vw, 900px"
      {...props}
      className={`rounded-lg${props.className ? ` ${props.className}` : ""}`}
    />
  );
}

export function getMDXComponents(components?: MDXComponents) {
  return {
    ...defaultMdxComponents,
    img: DocImage,
    Mermaid,
    ...components,
  } satisfies MDXComponents;
}

export const useMDXComponents = getMDXComponents;

declare global {
  type MDXProvidedComponents = ReturnType<typeof getMDXComponents>;
}
