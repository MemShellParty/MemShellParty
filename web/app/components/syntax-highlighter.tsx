import { PrismLight as SyntaxHighlighter } from "react-syntax-highlighter";
import java from "react-syntax-highlighter/dist/esm/languages/prism/java";

SyntaxHighlighter.registerLanguage("java", java);

export default SyntaxHighlighter;
