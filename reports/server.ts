// Mesh+ reports: the iPhone app POSTs every report and block here, and each goes to the developer on Telegram
// through @frdy_bot, who acts on it within 24 hours (meshplus.app/terms.html).
// Env: TELEGRAM_TOKEN (@frdy_bot), TELEGRAM_CHAT (the developer's chat id), PORT.
const { TELEGRAM_TOKEN, TELEGRAM_CHAT, PORT = "3000" } = process.env

// ponytail: per-IP limit kept in memory, resets on restart; fine for one small instance
const hits = new Map<string, number[]>()
function limited(ip: string) {
  const now = Date.now()
  const recent = (hits.get(ip) ?? []).filter((t) => now - t < 3_600_000)
  hits.set(ip, [...recent, now])
  if (hits.size > 10_000) hits.clear()
  return recent.length >= 30
}

const str = (v: unknown, max: number) => (typeof v === "string" ? v : "").slice(0, max)
export function message(r: any) {
  const node = str(r.node, 9)
  if (!/^![0-9a-f]{8}$/.test(node)) return null
  const kind = r.kind === "block" ? "Block" : "Report"
  const name = str(r.name, 40).replace(/[\r\n]/g, " ")
  const messages = (Array.isArray(r.messages) ? r.messages : []).slice(0, 10)
    .map((m: any) => `${str(m?.time, 40)}  ${str(m?.text, 240).replace(/\n/g, " ")}`)
  return [
    `Mesh+ ${r.demo === true ? "[demo] " : ""}${kind.toLowerCase()}: ${node} ${name}`.trim(),
    `From Mesh+ ${str(r.app, 20)} on ${str(r.os, 30)}, in ${str(r.place, 80) || "-"}`,
    "",
    kind === "Report" ? "Reported message:" : "Their latest messages on the reporter's phone:",
    messages.join("\n") || "(none)",
    "",
    `To remove this node from Mesh+ for everyone, add "${node}" to docs/blocklist.json in meshtastic-plus.`,
  ].join("\n")
}

if (import.meta.main) Bun.serve({
  port: Number(PORT),
  async fetch(req) {
    const { pathname } = new URL(req.url)
    if (pathname === "/health") return new Response("ok")
    if (req.method !== "POST" || pathname !== "/report") return new Response("Not found", { status: 404 })
    if (limited(req.headers.get("x-forwarded-for")?.split(",")[0].trim() ?? "")) return new Response("Too many reports", { status: 429 })
    const raw = await req.text()
    if (raw.length > 16_000) return new Response("Too large", { status: 413 })
    let data
    try { data = JSON.parse(raw) } catch { return new Response("Bad JSON", { status: 400 }) }
    const text = message(data)
    if (!text) return new Response("Bad node", { status: 400 })
    const res = await fetch(`https://api.telegram.org/bot${TELEGRAM_TOKEN}/sendMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chat_id: TELEGRAM_CHAT, text, link_preview_options: { is_disabled: true } }),
    })
    if (!res.ok) { console.error("telegram", res.status, await res.text()); return new Response("Try again later", { status: 502 }) }
    return new Response("ok")
  },
})
