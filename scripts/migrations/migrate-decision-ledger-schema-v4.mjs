import fs from "node:fs";
import path from "node:path";

const [sourceArgument, targetArgument] = process.argv.slice(2);
if (!sourceArgument || !targetArgument) {
  throw new Error(
    "Usage: node scripts/migrations/migrate-decision-ledger-schema-v4.mjs <source> <target>",
  );
}

const source = path.resolve(sourceArgument);
const target = path.resolve(targetArgument);
if (source === target) {
  throw new Error("Source and target decision ledgers must differ.");
}

const ledger = JSON.parse(fs.readFileSync(source, "utf8"));
if (ledger.schemaVersion !== "1.0" || !Array.isArray(ledger.assets)) {
  throw new Error("Unsupported decision ledger.");
}

const evidenceOnlyMainlines = new Set(["L-05", "L-06", "L-07"]);
const assets = ledger.assets.map((asset) => {
  if (asset.routeDecision !== "PUBLISH_CANDIDATE") {
    return asset;
  }

  const migrated = {
    ...asset,
    targetContentVersion: "2026-07-29.1",
  };
  if (evidenceOnlyMainlines.has(asset.assetId)) {
    migrated.finalRoute = "EVIDENCE_ONLY";
    migrated.projectSlugs = [];
    migrated.caseSlugs = [];
    migrated.decisionReason =
      "Former theme Project migrated to a non-subject Collection; retain only its reviewed aggregate Evidence summary.";
  }
  if (asset.assetId === "T-05") {
    migrated.projectSlugs = ["role-reset-tool"];
    migrated.decisionReason =
      "Reviewed role-reset task now supports both its Case and the derived delivered Tool Project.";
  }
  return migrated;
});

fs.writeFileSync(
  target,
  `${JSON.stringify({ schemaVersion: ledger.schemaVersion, assets }, null, 2)}\n`,
  "utf8",
);
