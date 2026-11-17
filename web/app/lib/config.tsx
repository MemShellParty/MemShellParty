import type { LinkItemType } from "fumadocs-ui/layouts/shared";

import { useTranslation } from "react-i18next";

import { LanguageSwitcher } from "@/components/language-switcher";

type NavLabelKey =
  | "MemShellGenerator"
  | "ProbeShellGenerator"
  | "BytecodeTools"
  | "MCP"
  | "documents"
  | "about";

function NavLabel({ translationKey }: { translationKey: NavLabelKey }) {
  const { t } = useTranslation("common");

  return t(translationKey);
}

export const siteConfig = {
  name: "MemShellParty",
  url: "https://party.memshell.news",
  github: "https://github.com/MemShellParty/MemShellParty",
  latestRelease: "https://github.com/MemShellParty/MemShellParty/releases/latest",
  author: "ReaJason",
  authorGithub: "https://github.com/ReaJason",
  authorIntro: "Java RASP Developer",
  blog: "https://reajason.eu.org",
  navLinks: [
    {
      text: <NavLabel translationKey="MemShellGenerator" />,
      url: "/memshell",
    },
    {
      text: <NavLabel translationKey="ProbeShellGenerator" />,
      url: "/probeshell",
    },
    {
      text: <NavLabel translationKey="BytecodeTools" />,
      url: "/bytecode",
    },
    {
      text: <NavLabel translationKey="MCP" />,
      url: "/mcp",
    },
    {
      text: <NavLabel translationKey="documents" />,
      url: "/docs",
    },
    {
      text: <NavLabel translationKey="about" />,
      url: "/about",
    },
    {
      type: "custom",
      children: <LanguageSwitcher />,
      secondary: true,
    },
  ] as LinkItemType[],
};
