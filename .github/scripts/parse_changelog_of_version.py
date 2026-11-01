import argparse
import sys

if __name__ == '__main__':
    capture = False
    result_lines = []
    parser = argparse.ArgumentParser(description="Extract changelog for a specific version")
    parser.add_argument("version", help="The version of the changelog to extract, e.g. 'v1.0.0'")
    args = parser.parse_args()
    version = args.version

    with open("../../web/content/docs/changelog.mdx") as f:
        lines = f.readlines()
        for line in lines:
            if line.startswith(f"## [{version}]"):
                capture = True
            elif capture and line.startswith("## ["):
                break
            elif capture:
                result_lines.append(line)
    if not result_lines:
        print("Specified version not found.", file=sys.stderr)
        sys.exit(1)
    version_without_v = version[1:] if version.startswith("v") else version

    result_lines.append("## 更新方式\n\n")
    result_lines.append("Web 端内置 Web 界面、REST API 与 MCP Server（<http://127.0.0.1:8080/mcp>），需 JDK 17 及以上。\n\n")
    result_lines.append("Docker 部署：\n")
    result_lines.append("```bash\n")
    result_lines.append("docker rm -f memshell-party\n\n")
    result_lines.append("docker run --pull=always --rm -it -d -p 8080:8080 --name memshell-party reajason/memshell-party:latest\n")
    result_lines.append("```\n\n")
    result_lines.append(f"Jar 启动（`memshell-party-boot-{version_without_v}.jar`）：\n")
    result_lines.append("```bash\n")
    result_lines.append(f"java -jar --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.xml/com.sun.org.apache.xalan.internal.xsltc.trax=ALL-UNNAMED --add-opens=java.xml/com.sun.org.apache.xalan.internal.xsltc.runtime=ALL-UNNAMED memshell-party-boot-{version_without_v}.jar\n")
    result_lines.append("```\n\n")
    result_lines.append("其他产物：\n")
    result_lines.append(f"- `memshell-party-gui-{version_without_v}.jar`：桌面端，功能与 Web 端一致，JDK 8 及以上，双击或 `java -jar` 启动\n")
    result_lines.append(f"- `memshell-party-woodpecker-{version_without_v}.jar`：放进 Woodpecker 根目录的 `plugin/` 后重启，在 Helper 中使用\n")
    result_lines.append(f"- `memshell-party-godzilla-{version_without_v}.jar`：通过 Godzilla 插件管理添加后重启，生成时选择插件「MemShellParty」，完成后点「注入内存马」\n")
    result_lines.append(f"- `memshell-party-shiro-exploit-{version_without_v}.jar`：Shiro-550 利用工具，用于参考引用 `memshell-party-gui-sdk` 编写 exp，JDK 8 及以上，`java -jar` 启动\n")
    print("".join(result_lines).strip())