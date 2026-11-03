import { expect, test } from "bun:test"
import { message } from "./server"

test("builds the Telegram text and rejects bad nodes", () => {
  const text = message({ kind: "report", node: "!6a3f21c0", name: "Maya\nX", messages: [{ time: "t", text: "hi\nthere" }] })!
  expect(text.split("\n")[0]).toBe("Mesh+ report: !6a3f21c0 Maya X")
  expect(text).toContain("t  hi there")
  expect(text).toContain('"!6a3f21c0"')
  expect(message({ kind: "block", node: "!6a3f21c0", demo: true })!.split("\n")[0]).toBe("Mesh+ [demo] block: !6a3f21c0")
  expect(message({ kind: "block", node: "6a3f21c0" })).toBeNull()
  expect(message({ node: "!zz" })).toBeNull()
})
