// Before deploying (spec 017): reports which account deploys, which permissions it is missing and whether
// the three AI secrets are in Secret Manager (and whether any is only in the GitHub secrets).
const fs = require("fs");
const path = require("path");
const { GoogleAuth } = require(require.resolve("google-auth-library", { paths: [path.join(__dirname, "..", "functions")] }));

const PROJECT = "earcaster";
const SECRETS = ["ANTHROPIC_API_KEY", "ELEVENLABS_API_KEY", "CLAUDE_MODEL"];
// Permission used by the deploy → role that grants it
const NEEDED = {
  "serviceusage.services.get": "Editor",
  "serviceusage.services.enable": "Editor",
  "datastore.indexes.create": "Editor",
  "cloudfunctions.functions.create": "Administrador de Cloud Functions",
  "run.services.setIamPolicy": "Administrador de Cloud Run",
  "iam.serviceAccounts.actAs": "Usuario de cuenta de servicio",
  "secretmanager.secrets.setIamPolicy": "Administrador de Secret Manager",
};

async function main() {
  const email = JSON.parse(fs.readFileSync(process.env.GOOGLE_APPLICATION_CREDENTIALS, "utf8")).client_email;
  console.log(`Cuenta de despliegue: ${email}`);
  const auth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/cloud-platform"] });
  const token = await auth.getAccessToken();
  const api = async (method, url, body) => {
    const res = await fetch(url, {
      method,
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: body && JSON.stringify(body),
    });
    return { status: res.status, json: await res.json().catch(() => ({})) };
  };

  const test = await api("POST", `https://cloudresourcemanager.googleapis.com/v1/projects/${PROJECT}:testIamPermissions`,
    { permissions: Object.keys(NEEDED) });
  if (test.status !== 200) {
    console.log(`::warning::No se pudieron revisar los permisos (${test.status}).`);
  } else {
    const have = new Set(test.json.permissions || []);
    const missing = [...new Set(Object.keys(NEEDED).filter((p) => !have.has(p)).map((p) => NEEDED[p]))];
    console.log(missing.length ? `::warning::A la cuenta le faltan estos roles: ${missing.join(", ")}.`
      : "La cuenta tiene todos los permisos.");
  }

  const sm = `https://secretmanager.googleapis.com/v1/projects/${PROJECT}/secrets`;
  let absent = 0;
  for (const name of SECRETS) {
    const found = await api("GET", `${sm}/${name}/versions/latest`);
    const value = (process.env[name] || "").trim();
    if (found.status === 200) {
      console.log(`${name}: está en Secret Manager.`);
    } else if (found.status === 404 && value) {
      absent++;
      console.log(`::error::${name}: está en los secretos de GitHub, pero la función lo lee de Secret Manager.`);
    } else if (found.status === 404) {
      absent++;
      console.log(`::error::${name}: no está ni en Secret Manager ni en los secretos de GitHub.`);
    } else {
      console.log(`::warning::${name}: no se pudo revisar (${found.status}).`);
    }
  }
  if (absent) process.exit(1);
}

main().catch((e) => {
  console.log(`::error::${e.message}`);
  process.exit(1);
});
