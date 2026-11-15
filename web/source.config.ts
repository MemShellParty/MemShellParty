import { remarkImage, remarkMdxMermaid } from "fumadocs-core/mdx-plugins";
import { defineConfig } from "fumadocs-mdx/config";

import { remarkResponsiveImages } from "./app/lib/remark-responsive-images";

export default defineConfig({
  mdxOptions: {
    remarkPlugins: [
      remarkMdxMermaid,
      [remarkImage, { useImport: false, external: false }],
      remarkResponsiveImages,
    ],
  },
});
