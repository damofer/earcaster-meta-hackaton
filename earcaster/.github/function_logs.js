// If the function test fails (spec 017): shows the warnings and errors the function left in
// Cloud Logging during the last 15 minutes, to see what failed without opening the console.
const path = require("path");
const { GoogleAuth } = require(require.resolve("google-auth-library", { paths: [path.join(__dirname, "..", "functions")] }));

async function main() {
  const auth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/cloud-platform"] });
  const token = await auth.getAccessToken();
  const since = new Date(Date.now() - 15 * 60 * 1000).toISOString();
  const res = await fetch("https://logging.googleapis.com/v2/entries:list", {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      resourceNames: ["projects/earcaster"],
      filter: `resource.type="cloud_run_revision" AND severity>=WARNING AND timestamp>="${since}"`,
      orderBy: "timestamp asc",
      pageSize: 30,
    }),
  });
  const json = await res.json().catch(() => ({}));
  if (res.status !== 200) {
    console.log(`No se pudieron leer los registros (${res.status}).`);
    return;
  }
  const entries = json.entries || [];
  if (!entries.length) console.log("La función no dejó avisos ni errores.");
  for (const e of entries) {
    const text = e.textPayload || e.jsonPayload?.message || JSON.stringify(e.jsonPayload || {});
    console.log(`${e.timestamp} ${e.resource?.labels?.service_name || ""} ${e.severity}: ${String(text).slice(0, 1500)}`);
  }
}

main().catch((e) => console.log(`No se pudieron leer los registros: ${e.message}`));
