import type { Image, Root } from "mdast";
import type {
  MdxJsxAttribute,
  MdxJsxAttributeValueExpression,
  MdxJsxTextElement,
} from "mdast-util-mdx-jsx";
import type { MdxjsEsm } from "mdast-util-mdxjs-esm";

import { visit } from "unist-util-visit";

const RESPONSIVE_WIDTHS = [480, 768, 1200, 1600];
const SUPPORTED_IMAGE = /\.(?:avif|jpe?g|png|tiff?|webp)$/i;
const EXTERNAL_IMAGE = /^(?:[a-z][a-z\d+.-]*:|\/\/)/i;

function expression(value: string): MdxJsxAttributeValueExpression {
  return {
    type: "mdxJsxAttributeValueExpression",
    value,
    data: {
      estree: {
        type: "Program",
        sourceType: "module",
        body: [
          {
            type: "ExpressionStatement",
            expression: { type: "Identifier", name: value },
          },
        ],
      },
    },
  };
}

function importImage(local: string, source: string): MdxjsEsm {
  return {
    type: "mdxjsEsm",
    value: `import ${local} from ${JSON.stringify(source)};`,
    data: {
      estree: {
        type: "Program",
        sourceType: "module",
        body: [
          {
            type: "ImportDeclaration",
            source: { type: "Literal", value: source },
            attributes: [],
            specifiers: [
              {
                type: "ImportDefaultSpecifier",
                local: { type: "Identifier", name: local },
              },
            ],
          },
        ],
      },
    },
  };
}

function withQuery(url: string, query: string): string {
  const hashIndex = url.indexOf("#");
  const base = hashIndex === -1 ? url : url.slice(0, hashIndex);
  const hash = hashIndex === -1 ? "" : url.slice(hashIndex);
  return `${base}${base.includes("?") ? "&" : "?"}${query}${hash}`;
}

function imageImportPath(url: string): string {
  return url.startsWith(".") ? url : `./${url}`;
}

export function remarkResponsiveImages() {
  return (tree: Root) => {
    const imports: MdxjsEsm[] = [];
    let imageCount = 0;

    visit(tree, "image", (node: Image, index, parent) => {
      if (
        !parent ||
        index === undefined ||
        EXTERNAL_IMAGE.test(node.url) ||
        node.url.startsWith("/")
      ) {
        return;
      }

      const pathname = node.url.split(/[?#]/, 1)[0];
      const properties = node.data?.hProperties;
      const width = properties?.width;
      const height = properties?.height;
      const intrinsicWidth = Number(width);

      if (
        !SUPPORTED_IMAGE.test(pathname) ||
        (typeof width !== "string" && typeof width !== "number") ||
        (typeof height !== "string" && typeof height !== "number") ||
        !Number.isInteger(intrinsicWidth) ||
        intrinsicWidth <= 0
      ) {
        return;
      }

      const originalName = `__responsiveImage${imageCount}`;
      const srcsetName = `__responsiveImageSrcset${imageCount}`;
      imageCount += 1;

      const importPath = imageImportPath(node.url);
      const widths = [
        ...RESPONSIVE_WIDTHS.filter((candidate) => candidate < intrinsicWidth),
        intrinsicWidth,
      ];
      const srcsetPath = withQuery(importPath, `w=${widths.join(";")}&format=webp&as=srcset`);
      imports.push(importImage(originalName, importPath), importImage(srcsetName, srcsetPath));

      const attributes: MdxJsxAttribute[] = [
        { type: "mdxJsxAttribute", name: "alt", value: node.alt ?? "image" },
        { type: "mdxJsxAttribute", name: "src", value: expression(originalName) },
        { type: "mdxJsxAttribute", name: "srcSet", value: expression(srcsetName) },
        { type: "mdxJsxAttribute", name: "width", value: String(width) },
        { type: "mdxJsxAttribute", name: "height", value: String(height) },
      ];
      if (node.title) {
        attributes.push({ type: "mdxJsxAttribute", name: "title", value: node.title });
      }

      const element: MdxJsxTextElement = {
        type: "mdxJsxTextElement",
        name: "img",
        attributes,
        children: [],
        position: node.position,
      };
      parent.children[index] = element;
    });

    tree.children.unshift(...imports);
  };
}
