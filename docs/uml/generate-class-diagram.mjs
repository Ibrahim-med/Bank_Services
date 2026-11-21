// Generates docs/uml/class-diagram.svg (UML class diagram of the domain model).
import { writeFileSync } from "node:fs";

const OUT = process.argv[2];
const LH = 17, PAD = 8, FONT = 12.5, CW = 6.9; // line height, padding, font size, approx char width

const classes = {
  Utilisateur: { x: 40, y: 60, w: 280, attrs: ["- idUtilisateur : Integer", "- username : String", "- password : String «BCrypt»", "- role : String «ADMIN | CLIENT»", "- statut : StatutUtilisateur"], ops: ["+ isActive() : boolean"] },
  Client: { x: 480, y: 60, w: 280, attrs: ["- idClient : Integer", "- nom : String", "- prenom : String", "- email : String", "- telephone : String", "- adresse : String", "- comptes : List<CompteBancaire>"], ops: [] },
  Bill: { x: 960, y: 60, w: 280, attrs: ["- idBill : Integer", "- reference : String", "- provider : String", "- category : BillCategory", "- amount : BigDecimal", "- issueDate : LocalDate", "- dueDate : LocalDate", "- status : BillStatus", "- paidAt : LocalDateTime"], ops: [] },
  CompteBancaire: { x: 480, y: 420, w: 280, abstract: true, attrs: ["- idCompte : Integer", "- numero : String", "- solde : BigDecimal", "- decouvertAutorise : BigDecimal", "- tauxInteret : BigDecimal", "- dateCreation : LocalDate", "- statut : StatutCompte"], ops: ["# initialiserDateCreation() : void"] },
  BillPayment: { x: 960, y: 420, w: 280, attrs: ["- idBillPayment : Integer", "- reference : String", "- amount : BigDecimal", "- paymentDate : LocalDateTime", "- status : BillPaymentStatus"], ops: [] },
  TransactionBancaire: { x: 960, y: 700, w: 280, attrs: ["- idTransaction : Integer", "- typeTransaction : TypeTransaction", "- montant : BigDecimal", "- dateTransaction : LocalDateTime", "- description : String", "- statut : StatutTransaction"], ops: [] },
  AccountActivationToken: { x: 14, y: 420, w: 214, attrs: ["- id : Long", "- tokenHash : String «SHA-256»", "- expiryDate : LocalDateTime", "- used : boolean"], ops: [] },
  PasswordResetToken: { x: 244, y: 420, w: 214, attrs: ["- id : Long", "- tokenHash : String «SHA-256»", "- expiryDate : LocalDateTime", "- used : boolean"], ops: [] },
  CompteCourant: { x: 100, y: 760, w: 300, stereo: "«DiscriminatorValue COURANT»", attrs: [], ops: ["+ getDecouvertAutoriseCourant() : BigDecimal", "+ setDecouvertAutoriseCourant(m : BigDecimal)"] },
  CompteEpargne: { x: 430, y: 760, w: 300, stereo: "«DiscriminatorValue EPARGNE»", attrs: [], ops: ["+ getTauxInteretEpargne() : BigDecimal", "+ setTauxInteretEpargne(t : BigDecimal)"] },
};

const enums = [
  ["TypeTransaction", ["DEPOT", "RETRAIT", "VIREMENT", "PAIEMENT_FACTURE"]],
  ["StatutTransaction", ["VALIDEE", "REFUSEE"]],
  ["TypeCompte", ["COURANT", "EPARGNE"]],
  ["StatutCompte", ["ACTIVE", "CLOSED"]],
  ["StatutUtilisateur", ["PENDING_ACTIVATION", "ACTIVE", "DISABLED"]],
  ["BillCategory", ["ELECTRICITY", "WATER", "INTERNET", "TELEPHONE", "OTHER"]],
  ["BillStatus", ["UNPAID", "PAID", "CANCELLED"]],
  ["BillPaymentStatus", ["COMPLETED"]],
];

const esc = s => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
let svg = [];

function box(name, c, kind = "class") {
  const header = (c.abstract || kind === "enum" || c.stereo) ? 2 : 1;
  const hH = header * LH + PAD;
  const aH = Math.max(c.attrs.length, kind === "class" && c.ops.length && !c.attrs.length ? 0 : 0) * LH + (c.attrs.length ? PAD : 4);
  const oH = kind === "enum" ? 0 : c.ops.length * LH + (c.ops.length ? PAD : 4);
  c.h = hH + aH + oH;
  const stroke = kind === "enum" ? "#B7791F" : "#1F4E79";
  const fill = kind === "enum" ? "#FFF3DC" : "#DCE8F5";
  svg.push(`<g>`);
  svg.push(`<rect x="${c.x}" y="${c.y}" width="${c.w}" height="${c.h}" rx="5" fill="#fff" stroke="${stroke}" stroke-width="1.4"/>`);
  svg.push(`<path d="M${c.x + 0.7} ${c.y + hH} V${c.y + 5} Q${c.x + 0.7} ${c.y + 0.7} ${c.x + 5} ${c.y + 0.7} H${c.x + c.w - 5} Q${c.x + c.w - 0.7} ${c.y + 0.7} ${c.x + c.w - 0.7} ${c.y + 5} V${c.y + hH} Z" fill="${fill}"/>`);
  let ty = c.y + PAD / 2 + LH - 4;
  const cx = c.x + c.w / 2;
  if (header === 2) {
    const st = kind === "enum" ? "«enumeration»" : c.abstract ? "«abstract»" : c.stereo;
    svg.push(`<text x="${cx}" y="${ty}" text-anchor="middle" font-size="11" fill="#444">${esc(st)}</text>`);
    ty += LH;
  }
  svg.push(`<text x="${cx}" y="${ty}" text-anchor="middle" font-weight="bold" font-size="13.5" ${c.abstract ? 'font-style="italic"' : ""} fill="#10283f">${esc(name)}</text>`);
  svg.push(`<line x1="${c.x}" y1="${c.y + hH}" x2="${c.x + c.w}" y2="${c.y + hH}" stroke="${stroke}"/>`);
  let y = c.y + hH + PAD / 2 + LH - 4;
  for (const a of c.attrs) { svg.push(`<text x="${c.x + 9}" y="${y}" font-size="${FONT}">${esc(a)}</text>`); y += LH; }
  if (kind !== "enum") {
    const sepY = c.y + hH + aH;
    svg.push(`<line x1="${c.x}" y1="${sepY}" x2="${c.x + c.w}" y2="${sepY}" stroke="${stroke}"/>`);
    y = sepY + PAD / 2 + LH - 4;
    for (const o of c.ops) { svg.push(`<text x="${c.x + 9}" y="${y}" font-size="${FONT}">${esc(o)}</text>`); y += LH; }
  }
  svg.push(`</g>`);
  // overflow check
  for (const t of [...c.attrs, ...c.ops]) if (t.length * CW + 18 > c.w) console.warn("tight:", name, t);
}

for (const [n, c] of Object.entries(classes)) box(n, c);

// enum row
const EY = 960; let ex = 20; const EW = 150, EG = 8;
svg.push(`<rect x="8" y="${EY - 34}" width="${8 * (EW + EG) + 16}" height="175" rx="8" fill="none" stroke="#B7791F" stroke-dasharray="5 4"/>`);
svg.push(`<text x="20" y="${EY - 14}" font-size="12" fill="#8a5a12" font-weight="bold">package enums  (used as attribute types; stored as strings with @Enumerated(STRING))</text>`);
for (const [n, vals] of enums) {
  const c = { x: ex, y: EY, w: EW, attrs: vals, ops: [] };
  box(n, c, "enum"); ex += EW + EG;
}

const C = classes;
const L = [];
const midX = c => c.x + c.w / 2, midY = c => c.y + c.h / 2;
const right = c => c.x + c.w, bottom = c => c.y + c.h;

function line(pts, { dash = false, end = "open", start = null } = {}) {
  const d = pts.map((p, i) => `${i ? "L" : "M"}${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join(" ");
  L.push(`<path d="${d}" fill="none" stroke="#1F4E79" stroke-width="1.4" ${dash ? 'stroke-dasharray="6 4"' : ""} ${end ? `marker-end="url(#${end})"` : ""} ${start ? `marker-start="url(#${start})"` : ""}/>`);
}
function label(x, y, t, anchor = "middle", opts = "") {
  L.push(`<text x="${x}" y="${y}" ${opts.includes("font-size") ? "" : `font-size="12"`} text-anchor="${anchor}" ${opts.includes("fill=") ? "" : `fill="#1b3a57"`} ${opts}>${esc(t)}</text>`);
}
const name = (x, y, t, a = "middle") => label(x, y, t, a, 'font-style="italic" font-weight="bold"');

// 1. Utilisateur -> Client (login of)
{ const y = C.Utilisateur.y + 60; line([[right(C.Utilisateur), y], [C.Client.x, y]]);
  label(right(C.Utilisateur) + 8, y - 6, "0..1", "start"); label(C.Client.x - 8, y - 6, "0..1", "end"); name((right(C.Utilisateur) + C.Client.x) / 2, y + 16, "login of"); }
// 2. Client *-- CompteBancaire (owns)
{ const x = midX(C.Client); line([[x, bottom(C.Client)], [x, C.CompteBancaire.y]], { start: "diamond", end: null });
  label(x + 8, bottom(C.Client) + 28, "1", "start"); label(x + 8, C.CompteBancaire.y - 8, "0..*", "start"); name(x - 8, (bottom(C.Client) + C.CompteBancaire.y) / 2 + 12, "owns", "end"); }
// 3. Bill -> Client (issued to)
{ const y = C.Bill.y + 60; line([[C.Bill.x, y], [right(C.Client), y]]);
  label(C.Bill.x - 8, y - 6, "0..*", "end"); label(right(C.Client) + 8, y - 6, "1", "start"); name((right(C.Client) + C.Bill.x) / 2, y + 16, "issued to"); }
// 4. BillPayment -> Bill (pays)
{ const x = midX(C.Bill); line([[x, C.BillPayment.y], [x, bottom(C.Bill)]]);
  label(x + 8, C.BillPayment.y - 8, "0..1", "start"); label(x + 8, bottom(C.Bill) + 18, "1", "start"); name(x - 8, (bottom(C.Bill) + C.BillPayment.y) / 2 + 4, "pays", "end"); }
// 5. BillPayment -> CompteBancaire (debits)
{ const y = C.BillPayment.y + 50; line([[C.BillPayment.x, y], [right(C.CompteBancaire), y]]);
  label(C.BillPayment.x - 8, y - 6, "0..*", "end"); label(right(C.CompteBancaire) + 8, y - 6, "1", "start"); name((right(C.CompteBancaire) + C.BillPayment.x) / 2, y + 16, "debits"); }
// 6. BillPayment -> TransactionBancaire (recorded as)
{ const x = midX(C.BillPayment); line([[x, bottom(C.BillPayment)], [x, C.TransactionBancaire.y]]);
  label(x + 8, bottom(C.BillPayment) + 18, "0..1", "start"); label(x + 8, C.TransactionBancaire.y - 8, "1", "start"); name(x - 8, (bottom(C.BillPayment) + C.TransactionBancaire.y) / 2 + 4, "recorded as", "end"); }
// 7. TransactionBancaire -> CompteBancaire (source / destination)
{ const tx = C.TransactionBancaire.x, cb = right(C.CompteBancaire);
  const y1 = C.TransactionBancaire.y + 40, y2 = C.TransactionBancaire.y + 110;
  const by1 = bottom(C.CompteBancaire) - 60, by2 = bottom(C.CompteBancaire) - 20;
  const kx1 = cb + 70, kx2 = cb + 40;
  line([[tx, y1], [kx1, y1], [kx1, by1], [cb, by1]]);
  line([[tx, y2], [kx2, y2], [kx2, by2], [cb, by2]]);
  label(tx - 8, y1 - 6, "0..*", "end"); label(cb + 6, by1 - 6, "0..1", "start");
  label(tx - 8, y2 - 6, "0..*", "end"); label(cb + 6, by2 + 15, "0..1", "start");
  name((tx + kx1) / 2, y1 + 16, "compteSource");
  name((tx + kx2) / 2 + 18, y2 + 16, "compteDestination"); }
// 8. Inheritance
{ const px = midX(C.CompteBancaire), pb = bottom(C.CompteBancaire), jy = pb + (C.CompteCourant.y - pb) / 2;
  L.push(`<path d="M${midX(C.CompteCourant)} ${C.CompteCourant.y} V${jy} H${midX(C.CompteEpargne)} V${C.CompteEpargne.y} M${px} ${jy} V${pb + 14}" fill="none" stroke="#1F4E79" stroke-width="1.4"/>`);
  L.push(`<path d="M${px} ${pb} L${px - 9} ${pb + 14} L${px + 9} ${pb + 14} Z" fill="#fff" stroke="#1F4E79" stroke-width="1.4"/>`);
  label(px - 12, jy - 8, "SINGLE_TABLE inheritance (type_compte)", "end", 'font-size="11" fill="#555"'); }
// 9. Tokens -> Utilisateur
for (const t of [C.AccountActivationToken, C.PasswordResetToken]) {
  const x = midX(t), ux = t === C.AccountActivationToken ? C.Utilisateur.x + 70 : right(C.Utilisateur) - 70;
  line([[x, t.y], [x, t.y - 40], [ux, t.y - 40], [ux, bottom(C.Utilisateur)]]);
  label(x + 6, t.y - 8, "0..*", "start"); label(ux + 6, bottom(C.Utilisateur) + 16, "1", "start");
}

const W = 1300, H = EY + 150;
const out = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="Arial, Helvetica, sans-serif">
<defs>
  <marker id="open" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" orient="auto-start-reverse" markerUnits="userSpaceOnUse"><path d="M1 1 L11 6 L1 11" fill="none" stroke="#1F4E79" stroke-width="1.5"/></marker>
  <marker id="diamond" viewBox="0 0 20 12" refX="1" refY="6" markerWidth="20" markerHeight="12" orient="auto" markerUnits="userSpaceOnUse"><path d="M1 6 L10 1 L19 6 L10 11 Z" fill="#1F4E79"/></marker>
</defs>
<rect width="100%" height="100%" fill="#ffffff"/>
<text x="20" y="32" font-size="19" font-weight="bold" fill="#10283f">BankServices — UML class diagram (domain model, package com.bankservices.bankservices_api.entity)</text>
${L.join("\n")}
${svg.join("\n")}
</svg>`;
writeFileSync(OUT, out);
console.log("ok", OUT);
