import { Plugin } from "@opencode/plugin"

const SDLC_GATE = `SDLC GATE (RAG Systems):
- Any accepted plan must go through the SDLC in docs/process/asdlc.md.
- ALWAYS execute all tests after implementation before finishing: run scripts/test.bat (Windows) or scripts/test.sh (Unix), then dev check [module] (tests + SonarQube).
- EXCEPTION: non-code changes (docs, .claude/ and .opencode/ assets, scripts/*.sh|*.bat|*.ps1, docker/**, CI workflows; no .java, no pom/mvnw/sonar-project.properties changes) skip the test+SonarQube gate — see asdlc.md Rule 1.
- Never commit/push to main directly; work in a per-feature clone on branch feat/<name>.
- Do not open a PR or merge until tests pass.
- Use dev auto-merge (alias dev merge) to merge green PRs, auto-resolve conflicts, and delete the feature clone on success.`

const JAVA_SOURCE = /\.java$/
const JAVA_CHANGE_TOOLS = new Set(["edit", "write"])
const JAVA_REMINDER =
  "SDLC gate: Java source modified. Before finishing, run scripts/test.sh (Windows: scripts/test.bat) then dev check [module] (tests + SonarQube). Do not open a PR/merge until tests pass."

function changedFile(input: unknown): string {
  if (typeof input !== "object" || input === null) return ""
  const args = input as { filePath?: unknown; path?: unknown }
  const file = args.filePath ?? args.path
  return typeof file === "string" ? file : ""
}

export default Plugin.define({
  id: "rag.sdlc-gate",
  async setup(ctx) {
    await ctx.session.hook("context", (event) => {
      if (event.system.some((part) => part.text.includes("SDLC GATE"))) return
      event.system.push({ type: "text", text: SDLC_GATE })
    })
    await ctx.tool.hook("execute.after", (event) => {
      if (event.status !== "completed" || !JAVA_CHANGE_TOOLS.has(event.tool)) return
      if (!JAVA_SOURCE.test(changedFile(event.input))) return
      const content = event.result.content
      event.result = {
        ...event.result,
        content:
          typeof content === "string"
            ? `${content}\n\n${JAVA_REMINDER}`
            : [...(content ?? []), { type: "text" as const, text: JAVA_REMINDER }],
      }
    })
  },
})
