// Generates database/er-diagram.svg - physical ER diagram (crow's foot notation)
// of the BankServices MySQL schema. Usage: node generate-er-diagram.mjs <out.svg>
import { writeFileSync } from "node:fs";

const OUT = process.argv[2];
const RH = 19, HH = 30, W = 340;

// [key, column, type, nullable]
const T = {
  utilisateur: { x: 30, y: 80, cols: [
    ["PK", "id_utilisateur", "INT AUTO_INCREMENT"], ["UK", "username", "VARCHAR(50)"], ["", "password", "VARCHAR(255)  BCrypt"],
    ["", "role", "VARCHAR(20)  ADMIN|CLIENT"], ["", "statut", "VARCHAR(20)  CHECK"], ["FK UK", "id_client", "INT", true]] },
  client: { x: 420, y: 80, cols: [
    ["PK", "id_client", "INT AUTO_INCREMENT"], ["", "nom", "VARCHAR(50)"], ["", "prenom", "VARCHAR(50)"],
    ["UK", "email", "VARCHAR(100)", true], ["", "telephone", "VARCHAR(20)", true], ["", "adresse", "VARCHAR(255)", true]] },
  bill: { x: 810, y: 80, cols: [
    ["PK", "id_bill", "INT AUTO_INCREMENT"], ["UK", "reference", "VARCHAR(40)"], ["", "provider", "VARCHAR(100)"],
    ["", "category", "VARCHAR(20)  CHECK"], ["", "amount", "DECIMAL(15,2)  > 0"], ["", "issue_date", "DATE"],
    ["", "due_date", "DATE  >= issue_date"], ["", "status", "VARCHAR(20)  UNPAID|PAID|CANCELLED"], ["FK", "id_client", "INT"],
    ["", "created_at", "DATETIME"], ["", "paid_at", "DATETIME", true]] },
  password_reset_token: { x: 30, y: 360, cols: [
    ["PK", "id", "BIGINT AUTO_INCREMENT"], ["UK", "token_hash", "VARCHAR(64)  SHA-256"], ["FK", "id_utilisateur", "INT  ON DELETE CASCADE"],
    ["", "expiry_date", "DATETIME"], ["", "used", "TINYINT(1)"], ["", "created_at", "DATETIME"]] },
  account_activation_token: { x: 30, y: 600, cols: [
    ["PK", "id", "BIGINT AUTO_INCREMENT"], ["UK", "token_hash", "VARCHAR(64)  SHA-256"], ["FK", "id_utilisateur", "INT  ON DELETE CASCADE"],
    ["", "expiry_date", "DATETIME"], ["", "used", "TINYINT(1)"], ["", "created_at", "DATETIME"]] },
  compte_bancaire: { x: 420, y: 360, cols: [
    ["PK", "id_compte", "INT AUTO_INCREMENT"], ["UK", "numero", "VARCHAR(30)"], ["", "solde", "DECIMAL(15,2)  >= -decouvert"],
    ["", "type_compte", "VARCHAR(20)  COURANT|EPARGNE"], ["", "decouvert_autorise", "DECIMAL(15,2)  >= 0"], ["", "taux_interet", "DECIMAL(5,2)  >= 0"],
    ["", "date_creation", "DATE"], ["FK", "id_client", "INT"], ["", "statut", "VARCHAR(20)  ACTIVE|CLOSED"]] },
  bill_payment: { x: 810, y: 430, cols: [
    ["PK", "id_bill_payment", "INT AUTO_INCREMENT"], ["UK", "reference", "VARCHAR(40)"], ["FK UK", "id_bill", "INT"],
    ["FK", "id_compte", "INT"], ["FK UK", "id_transaction", "INT"], ["", "amount", "DECIMAL(15,2)  > 0"],
    ["", "payment_date", "DATETIME"], ["", "status", "VARCHAR(20)  COMPLETED"]] },
  transaction_bancaire: { x: 420, y: 680, cols: [
    ["PK", "id_transaction", "INT AUTO_INCREMENT"], ["", "type_transaction", "ENUM  DEPOT|RETRAIT|VIREMENT|PAIEMENT_FACTURE"],
    ["", "montant", "DECIMAL(15,2)  > 0"], ["", "date_transaction", "DATETIME"], ["", "description", "VARCHAR(255)", true],
    ["", "statut", "ENUM  VALIDEE|REFUSEE"], ["FK", "id_compte_source", "INT", true], ["FK", "id_compte_destination", "INT", true]] },
};

const esc = s => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
const out = [];
for (const [name, t] of Object.entries(T)) {
  t.w = name === "transaction_bancaire" ? 470 : W;
  t.h = HH + t.cols.length * RH + 6;
  out.push(`<rect x="${t.x}" y="${t.y}" width="${t.w}" height="${t.h}" rx="5" fill="#fff" stroke="#1F4E79" stroke-width="1.4"/>`);
  out.push(`<path d="M${t.x + .7} ${t.y + HH} V${t.y + 5} Q${t.x + .7} ${t.y + .7} ${t.x + 5} ${t.y + .7} H${t.x + t.w - 5} Q${t.x + t.w - .7} ${t.y + .7} ${t.x + t.w - .7} ${t.y + 5} V${t.y + HH} Z" fill="#1F4E79"/>`);
  out.push(`<text x="${t.x + 10}" y="${t.y + 20}" font-size="14" font-weight="bold" fill="#fff">${name}</text>`);
  t.cols.forEach(([k, c, ty, nul], i) => {
    const y = t.y + HH + i * RH + 14;
    if (i % 2) out.push(`<rect x="${t.x + 1}" y="${y - 13}" width="${t.w - 2}" height="${RH}" fill="#F2F6FB"/>`);
    const kc = k.startsWith("PK") ? "#B7791F" : k.startsWith("FK") ? "#2a6db5" : "#7a8796";
    out.push(`<text x="${t.x + 8}" y="${y}" font-size="10" font-weight="bold" fill="${kc}">${k}</text>`);
    out.push(`<text x="${t.x + 50}" y="${y}" font-size="12.5" ${k.includes("PK") ? 'font-weight="bold" text-decoration="underline"' : ""} fill="#14263a">${c}${nul ? '<tspan fill="#8a96a3"> ?</tspan>' : ""}</text>`);
    out.push(`<text x="${t.x + t.w - 8}" y="${y}" font-size="10.5" text-anchor="end" fill="#566476">${esc(ty)}</text>`);
  });
}
const rowY = (t, col) => t.y + HH + t.cols.findIndex(c => c[1] === col) * RH + 9;

// ---- relationship lines with crow's foot ends ----
const L = [];
const S = 'stroke="#1F4E79" stroke-width="1.4" fill="none"';
function end([x, y], [dx, dy], kind) { // (dx,dy) unit vector from box edge outward along the line
  const px = -dy, py = dx, p = (d, o) => [x + dx * d + px * o, y + dy * d + py * o];
  const seg = (a, b) => L.push(`<path d="M${a[0]} ${a[1]} L${b[0]} ${b[1]}" ${S}/>`);
  const circle = d => { const c = p(d, 0); L.push(`<circle cx="${c[0]}" cy="${c[1]}" r="4.5" fill="#fff" stroke="#1F4E79" stroke-width="1.4"/>`); };
  if (kind === "one") { seg(p(8, -6), p(8, 6)); seg(p(13, -6), p(13, 6)); }
  if (kind === "zeroOne") { seg(p(8, -6), p(8, 6)); circle(18); }
  if (kind === "many" || kind === "zeroMany") {
    seg(p(0, -7), p(13, 0)); seg(p(0, 7), p(13, 0));
    if (kind === "many") seg(p(17, -6), p(17, 6)); else circle(19);
  }
}
function rel(pts, a, b, label, lpos) {
  L.push(`<path d="M${pts.map(p => p.join(" ")).join(" L")}" ${S}/>`);
  const dir = (p, q) => { const dx = q[0] - p[0], dy = q[1] - p[1], n = Math.hypot(dx, dy); return [dx / n, dy / n]; };
  end(pts[0], dir(pts[0], pts[1]), a);
  const n = pts.length; end(pts[n - 1], dir(pts[n - 1], pts[n - 2]), b);
  if (label) L.push(`<text x="${lpos[0]}" y="${lpos[1]}" font-size="11" font-style="italic" fill="#2a6db5" text-anchor="${lpos[2] || "middle"}">${label}</text>`);
}
const { utilisateur: U, client: C, bill: B, compte_bancaire: K, bill_payment: P, transaction_bancaire: X,
  password_reset_token: R, account_activation_token: A } = T;
const R_ = t => t.x + t.w, B_ = t => t.y + t.h;

{ const y = rowY(U, "id_client"); rel([[R_(U), y], [C.x, y]], "zeroOne", "zeroOne", "id_client", [(R_(U) + C.x) / 2, y + 17]); }
{ const x = C.x + 170; rel([[x, B_(C)], [x, K.y]], "one", "zeroMany", "id_client", [x + 8, (B_(C) + K.y) / 2 + 4, "start"]); }
{ const y = rowY(B, "id_client"); rel([[B.x, y], [B.x - 25, y], [B.x - 25, C.y + 60], [R_(C), C.y + 60]], "zeroMany", "one"); }
{ const x = R.x + 170; rel([[x, B_(U)], [x, R.y]], "one", "zeroMany", "id_utilisateur", [x + 8, (B_(U) + R.y) / 2 + 4, "start"]); }
{ const y = rowY(A, "id_utilisateur"); rel([[A.x, y], [A.x - 16, y], [A.x - 16, U.y + 50], [U.x, U.y + 50]], "zeroMany", "one"); }
{ const x1 = K.x + 120, x2 = K.x + 230;
  rel([[x1, B_(K)], [x1, X.y]], "zeroOne", "zeroMany", "id_compte_source", [x1 - 6, (B_(K) + X.y) / 2 + 4, "end"]);
  rel([[x2, B_(K)], [x2, X.y]], "zeroOne", "zeroMany", "id_compte_destination", [x2 + 6, (B_(K) + X.y) / 2 + 4, "start"]); }
{ const x = P.x + 170; rel([[x, B_(B)], [x, P.y]], "one", "zeroOne", "id_bill (UNIQUE)", [x + 8, (B_(B) + P.y) / 2 + 4, "start"]); }
{ const y = rowY(P, "id_compte"); rel([[R_(K), y], [P.x, y]], "one", "zeroMany"); }
{ const y = rowY(X, "id_transaction") + 40, x = P.x + 170; rel([[R_(X), y], [x, y], [x, B_(P)]], "one", "zeroOne", "id_transaction (UNIQUE)", [x + 8, (y + B_(P)) / 2 + 4, "start"]); }

// legend
const LY = B_(X) + 40, LX = 30;
const legend = [];
legend.push(`<text x="${LX}" y="${LY}" font-size="12" font-weight="bold" fill="#14263a">Legend</text>`);
const items = [["one", "exactly one"], ["zeroOne", "zero or one"], ["zeroMany", "zero or many"]];
items.forEach(([k, t], i) => { const x = LX + 70 + i * 190; L.push(`<path d="M${x} ${LY - 4} L${x + 60} ${LY - 4}" ${S}/>`); end([x + 60, LY - 4], [-1, 0], k); legend.push(`<text x="${x + 68}" y="${LY}" font-size="11.5" fill="#14263a">${t}</text>`); });
legend.push(`<text x="${LX + 640}" y="${LY}" font-size="11.5" fill="#14263a"><tspan fill="#B7791F" font-weight="bold">PK</tspan> primary key   <tspan fill="#2a6db5" font-weight="bold">FK</tspan> foreign key   <tspan fill="#7a8796" font-weight="bold">UK</tspan> unique   ? nullable</text>`);

const WD = 1180, HT = LY + 20;
writeFileSync(OUT, `<svg xmlns="http://www.w3.org/2000/svg" width="${WD}" height="${HT}" viewBox="0 0 ${WD} ${HT}" font-family="Arial, Helvetica, sans-serif">
<rect width="100%" height="100%" fill="#fff"/>
<text x="30" y="38" font-size="20" font-weight="bold" fill="#10283f">BankServices — database schema (MySQL 8, InnoDB, utf8mb4)</text>
<text x="30" y="60" font-size="12" fill="#566476">8 tables · created by Flyway migrations V1–V3 · CHECK constraints enforce the business rules at database level</text>
${L.join("\n")}
${out.join("\n")}
${legend.join("\n")}
</svg>`);
console.log("ok", OUT, WD, HT);
