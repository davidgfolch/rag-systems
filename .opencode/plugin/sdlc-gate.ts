import type { Plugin } from "@opencode-ai/plugin"

const SDLC_GATE = `SDLC GATE (RAG Systems):
- Any accepted plan must go through the SDLC in docs/process/asdlc.md.
- ALWAYS execute all tests after implementation before finishing: run scripts/test.bat (Windows) or scripts/test.sh (Unix), then dev check [module] (tests + SonarQube).
- EXCEPTION: docs/skills/rules/config-only changes (no .java, no build/script/pom changes) skip the test+SonarQube gate — see asdlc.md Rule 1.
- Never commit/push to main directly; work in a per-feature clone on branch feat/<name>.
- Do not open a PR or merge until tests pass.
- Use dev auto-merge (alias dev merge) to merge green PRs, auto-resolve conflicts, and delete the feature clone on success.`

export default (async ({ directory }) => {
  return {
    "experimental.chat.system.transform": async (_input, output) => {
      if (!output.system.some((s) => s.includes("SDLC GATE"))) {
        output.system.push(SDLC_GATE)
      }
    },
    "tool.execute.after": async (input, output) => {
      if (input.tool === "edit" || input.tool === "write") {
        const args = input.args ?? {}
        const file = args.filePath ?? args.path ?? ""
        if (/\.(java)$/.test(typeof file === "string" ? file : "")) {
          output.title = `${output.title} — SDLC: run tests after Java change`
          output.output += `\n\nSDLC gate: Java source modified. Before finishing, run scripts/test.sh (Windows: scripts/test.bat) then dev check [module] (tests + SonarQube). Do not open a PR/merge until tests pass.`
        }
      }
    },
  }
}) satisfies Plugin
