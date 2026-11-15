import { type HTMLProps, type ReactNode, lazy, Suspense } from "react";
import materialDark from "react-syntax-highlighter/dist/esm/styles/prism/material-dark";

import { CopyButton } from "@/components/copy-button";
import { CopyMenuButton, type CopyOption } from "@/components/copy-menu-button";
import { cn } from "@/lib/utils";

const SyntaxHighlighter = lazy(() => import("@/components/syntax-highlighter"));

export default function CodeViewer({
  code,
  header,
  button,
  language,
  height,
  fillHeight = false,
  showLineNumbers = true,
  wrapLongLines = true,
  copyLabel,
  copyDisabled = false,
  copyOptions,
  onCopy,
}: Readonly<CodeViewerProps>) {
  const lineProps: lineTagPropsFunction | HTMLProps<HTMLElement> | undefined = wrapLongLines
    ? { style: { overflowWrap: "break-word", whiteSpace: "pre-wrap" } }
    : undefined;
  return (
    <div
      className={cn("rounded-lg border", fillHeight && "xl:flex xl:h-full xl:min-h-0 xl:flex-col")}
    >
      <div
        className={cn(
          "flex shrink-0 items-center justify-end border-b p-2",
          header && "justify-between",
        )}
      >
        {header}
        <div className="flex items-center gap-2">
          {button}
          {copyOptions ? (
            <CopyMenuButton
              options={copyOptions}
              variant="ghost"
              size="sm"
              aria-label={copyLabel}
              title={copyLabel}
            />
          ) : (
            <CopyButton
              value={code}
              variant="ghost"
              size="sm"
              aria-label={copyLabel}
              title={copyLabel}
              disabled={copyDisabled}
              onCopy={onCopy}
            />
          )}
        </div>
      </div>
      <div
        className={cn(
          "wrap-all relative overflow-hidden text-xs",
          fillHeight && "xl:min-h-0 xl:flex-1",
        )}
      >
        <Suspense
          fallback={
            <pre
              className={cn("m-0 overflow-auto p-4", fillHeight && "xl:h-full!")}
              style={{
                height,
                background: "#2f2f2f",
                color: "#eee",
                whiteSpace: wrapLongLines ? "pre-wrap" : "pre",
              }}
            >
              {code}
            </pre>
          }
        >
          <SyntaxHighlighter
            className={fillHeight ? "xl:h-full!" : undefined}
            language={language}
            style={materialDark}
            showLineNumbers={showLineNumbers}
            wrapLongLines={wrapLongLines}
            lineProps={lineProps}
            customStyle={{
              margin: 0,
              padding: showLineNumbers ? 0 : "1em 1em",
              borderRadius: "0 0 var(--radius) var(--radius)",
              height,
              whiteSpace: wrapLongLines ? "pre-wrap" : "pre",
              overflowWrap: wrapLongLines ? "normal" : "break-word",
            }}
          >
            {code}
          </SyntaxHighlighter>
        </Suspense>
      </div>
    </div>
  );
}

interface CodeViewerProps {
  code: string;
  language: string;
  header?: ReactNode;
  button?: ReactNode;
  height?: string | number;
  /** 桌面双栏下填满结果面板剩余高度，小屏幕仍使用 height。 */
  fillHeight?: boolean;
  showLineNumbers?: boolean;
  wrapLongLines?: boolean;
  lineProps?: (lineNumber: number) => React.HTMLProps<HTMLElement>;
  copyLabel?: string;
  copyDisabled?: boolean;
  copyOptions?: CopyOption[];
  /** 复制 code 本体成功后触发（copyOptions 场景请在选项上单独设置 onCopy） */
  onCopy?: () => void;
}
