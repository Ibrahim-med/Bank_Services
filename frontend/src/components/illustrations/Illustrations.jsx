/*
 * BankServices line-art illustrations (original artwork).
 * - Pure SVG, no external assets, a few KB each.
 * - Coloured only through CSS classes defined in styles/design-tokens.css
 *   (.p paper, .b blue, .k sky, .n sand, .l line, .dash ...), which read
 *   the --ill-* variables: change the variables to recolour (dark mode,
 *   brand bands, empty states).
 * - Decorative: always aria-hidden. Groups carry `ill-a-*` classes that
 *   the page can animate (see styles/home.css).
 */

const svgProps = (className, viewBox) => ({
    viewBox,
    className: `ill ${className}`.trim(),
    "aria-hidden": true,
    focusable: "false",
    xmlns: "http://www.w3.org/2000/svg"
});

/* Small reusable bits ---------------------------------------------------- */

function PaperPlane({ x, y, scale = 1 }) {
    return (
        <g transform={`translate(${x} ${y}) scale(${scale})`}>
            <path className="p" d="M0 22 L64 0 L30 46 L24 30 Z" />
            <path className="l" d="M24 30 L64 0" />
            <path className="fm" d="M24 30 L30 46 L34 33 Z" />
        </g>
    );
}

function Plant({ x, y, scale = 1 }) {
    return (
        <g transform={`translate(${x} ${y}) scale(${scale})`}>
            <path className="k" d="M20 -40 C4 -78 -2 -106 8 -132 C26 -104 30 -72 20 -40 Z" />
            <path className="b" d="M24 -40 C34 -88 52 -112 76 -124 C72 -88 54 -60 24 -40 Z" />
            <path className="n" d="M18 -38 C8 -64 -16 -80 -36 -84 C-28 -58 -6 -40 18 -38 Z" />
            <path className="lt" d="M20 -40 C16 -70 12 -96 8 -128 M24 -40 C40 -70 56 -96 74 -120" />
            <path className="p" d="M-6 -40 H50 L42 0 H2 Z" />
            <path className="l" d="M-2 -26 H46" />
        </g>
    );
}

function CoinStack({ x, y, count = 4 }) {
    return (
        <g transform={`translate(${x} ${y})`}>
            {Array.from({ length: count }, (_, i) => {
                const top = -14 * (i + 1);
                return (
                    <g key={i}>
                        <path className="n" d={`M-34 ${top} v14 a34 9 0 0 0 68 0 v-14`} />
                        <ellipse className="n" cx="0" cy={top} rx="34" ry="9" />
                    </g>
                );
            })}
            <ellipse className="lt" cx="0" cy={-14 * count} rx="20" ry="4.5" />
        </g>
    );
}

function Sparkles({ items }) {
    return items.map(([kind, cx, cy, r], index) => (
        <circle
            key={index}
            className={kind === "ring" ? "l" : kind === "sand" ? "fn" : kind === "sky" ? "fk" : "fi"}
            cx={cx}
            cy={cy}
            r={r}
        />
    ));
}

/* Hero: phone with balance screen, bank card, coins, plant, paper plane --- */

export function HeroIllustration({ className = "" }) {
    const rows = [266, 304, 342];

    return (
        <svg {...svgProps(className, "0 0 560 470")}>
            <path
                className="blob ill-a-blob"
                d="M118 300 C86 206 152 118 254 104 C356 90 456 122 484 214 C512 306 470 392 378 416 C286 440 150 398 118 300 Z"
            />
            <line className="l" x1="24" y1="440" x2="536" y2="440" />

            <g className="ill-a-plant">
                <Plant x={60} y={440} scale={0.82} />
            </g>

            {/* Phone */}
            <g className="ill-a-phone">
                <rect className="p" x="226" y="58" width="184" height="352" rx="30" />
                <rect className="t" x="240" y="74" width="156" height="320" rx="20" />
                <rect className="fi" x="292" y="82" width="52" height="8" rx="4" />

                <rect className="fm" x="256" y="110" width="58" height="7" rx="3.5" />
                <rect className="fb" x="256" y="124" width="112" height="16" rx="8" />

                <rect className="p" x="254" y="156" width="128" height="84" rx="12" />
                <path className="fa" d="M264 226 L284 206 L304 214 L326 188 L348 198 L372 176 L372 230 L264 230 Z" />
                <polyline className="lb" points="264,226 284,206 304,214 326,188 348,198 372,176" />
                <circle className="fb" cx="326" cy="188" r="4.5" />

                {rows.map((y, index) => (
                    <g key={y}>
                        <circle className={index === 1 ? "k" : "n"} cx="268" cy={y} r="11" />
                        <rect className="fi" x="286" y={y - 8} width="58" height="6" rx="3" opacity="0.8" />
                        <rect className="fm" x="286" y={y + 3} width="36" height="5" rx="2.5" />
                        <rect className="fb" x="352" y={y - 4} width="26" height="8" rx="4" opacity="0.75" />
                    </g>
                ))}
            </g>

            {/* Bank card */}
            <g className="ill-a-card">
                <g transform="rotate(-14 210 330)">
                    <rect className="b" x="104" y="282" width="200" height="124" rx="16" />
                    <rect className="n" x="126" y="312" width="34" height="26" rx="6" />
                    <path className="l" d="M126 325 H160 M143 312 V338" style={{ strokeWidth: 1.5 }} />
                    <circle className="fp" cx="262" cy="316" r="12" opacity="0.9" />
                    <circle className="fn" cx="278" cy="316" r="12" opacity="0.9" />
                    {[0, 1, 2, 3].map((group) => (
                        <g key={group}>
                            {[0, 1, 2].map((dot) => (
                                <circle key={dot} className="fp" cx={128 + group * 42 + dot * 8} cy="364" r="2.6" />
                            ))}
                        </g>
                    ))}
                    <rect className="fp" x="126" y="380" width="72" height="6" rx="3" opacity="0.85" />
                </g>
            </g>

            {/* Coins */}
            <g className="ill-a-coins">
                <CoinStack x={466} y={440} count={4} />
                <circle className="k" cx="508" cy="356" r="24" />
                <circle className="l" cx="508" cy="356" r="14" />
            </g>

            {/* Paper plane with dashed trail */}
            <path className="dash ill-a-trail" d="M286 34 C352 0 420 18 452 62" />
            <g className="ill-a-plane">
                <PaperPlane x={452} y={40} scale={0.9} />
            </g>

            <Sparkles items={[
                ["ring", 168, 118, 9], ["dot", 196, 94, 3.5], ["sand", 524, 196, 7],
                ["ring", 534, 250, 6], ["dot", 140, 236, 3], ["sky", 186, 170, 5]
            ]} />
        </svg>
    );
}

/* How it works: vault with documents ------------------------------------- */

export function VaultIllustration({ className = "" }) {
    const ticks = Array.from({ length: 12 }, (_, i) => (i * Math.PI) / 6);

    return (
        <svg {...svgProps(className, "0 0 460 430")}>
            <path
                className="blob"
                d="M70 250 C48 160 120 80 226 74 C334 68 420 130 424 232 C428 334 352 396 248 400 C146 404 92 340 70 250 Z"
            />
            <line className="l" x1="20" y1="398" x2="440" y2="398" />

            <g className="ill-a-vault">
                <rect className="p" x="112" y="112" width="214" height="252" rx="26" />
                <rect className="t" x="132" y="132" width="174" height="212" rx="18" />
                <circle className="b" cx="214" cy="238" r="50" />
                <circle className="p" cx="214" cy="238" r="20" />
                {ticks.map((angle, index) => (
                    <line
                        key={index}
                        className="lw"
                        x1={214 + Math.cos(angle) * 32}
                        y1={238 + Math.sin(angle) * 32}
                        x2={214 + Math.cos(angle) * 42}
                        y2={238 + Math.sin(angle) * 42}
                    />
                ))}
                <path className="l" d="M214 238 L228 224" style={{ strokeWidth: 3 }} />
                <rect className="fi" x="282" y="222" width="10" height="34" rx="5" />
                <rect className="fi" x="122" y="168" width="8" height="24" rx="3" />
                <rect className="fi" x="122" y="286" width="8" height="24" rx="3" />
                <rect className="fi" x="140" y="364" width="28" height="14" rx="4" />
                <rect className="fi" x="270" y="364" width="28" height="14" rx="4" />
            </g>

            <g className="ill-a-docs">
                <g transform="rotate(9 360 320)">
                    <rect className="p" x="306" y="258" width="104" height="128" rx="12" />
                    <path className="lt" d="M322 284 H390 M322 300 H382 M322 316 H372 M322 332 H388" />
                    <circle className="k" cx="380" cy="360" r="13" />
                    <path className="l" d="M374 360 L379 365 L387 355" />
                </g>
            </g>

            <g className="ill-a-coin">
                <circle className="n" cx="72" cy="372" r="24" />
                <circle className="l" cx="72" cy="372" r="14" />
            </g>

            <path className="dash" d="M30 120 C60 70 110 52 150 62" />
            <PaperPlane x={146} y={36} scale={0.7} />

            <Sparkles items={[
                ["ring", 380, 96, 9], ["dot", 404, 128, 3.5], ["sand", 352, 70, 6],
                ["dot", 56, 210, 3], ["sky", 420, 180, 5]
            ]} />
        </svg>
    );
}

/* Security: phone with shield & lock -------------------------------------- */

export function SecurityIllustration({ className = "" }) {
    return (
        <svg {...svgProps(className, "0 0 440 440")}>
            <path
                className="blob"
                d="M84 226 C70 136 140 64 236 64 C334 64 404 132 400 230 C396 328 324 392 226 392 C130 392 98 316 84 226 Z"
            />
            <line className="l" x1="24" y1="410" x2="416" y2="410" />

            <g className="ill-a-phone">
                <rect className="p" x="140" y="60" width="164" height="318" rx="28" />
                <rect className="b" x="152" y="76" width="140" height="286" rx="18" />
                <rect className="fi" x="198" y="84" width="48" height="7" rx="3.5" />

                <path className="p ill-a-shield" d="M222 128 L272 146 V196 C272 232 250 256 222 268 C194 256 172 232 172 196 V146 Z" />
                <rect className="n" x="204" y="186" width="36" height="30" rx="6" />
                <path className="l" d="M210 186 V176 C210 162 234 162 234 176 V186" />
                <circle className="fi" cx="222" cy="200" r="4" />

                <rect className="fp" x="174" y="296" width="96" height="10" rx="5" opacity="0.9" />
                <rect className="fp" x="190" y="316" width="64" height="8" rx="4" opacity="0.55" />
            </g>

            <g className="ill-a-key">
                <circle className="n" cx="340" cy="318" r="22" />
                <circle className="p" cx="340" cy="318" r="8" />
                <path className="l" d="M358 330 L390 352 M378 344 L372 354 M386 350 L380 360" />
            </g>

            <Plant x={62} y={410} scale={0.7} />
            <path className="dash" d="M300 40 C340 20 380 30 398 62" />
            <PaperPlane x={392} y={44} scale={0.6} />

            <Sparkles items={[
                ["ring", 112, 112, 8], ["dot", 136, 88, 3], ["sand", 372, 160, 6],
                ["ring", 396, 214, 6], ["sky", 92, 300, 5]
            ]} />
        </svg>
    );
}

/* Final call to action: phone with completed transfer ---------------------- */

export function WelcomeIllustration({ className = "" }) {
    return (
        <svg {...svgProps(className, "0 0 440 400")}>
            <path
                className="blob"
                d="M62 250 C44 168 104 96 196 86 C290 76 384 116 396 206 C408 296 344 362 252 372 C160 382 80 330 62 250 Z"
            />
            <line className="l" x1="30" y1="370" x2="410" y2="370" />

            <g className="ill-a-phone">
                <rect className="p" x="148" y="34" width="170" height="318" rx="28" />
                <rect className="t" x="160" y="50" width="146" height="286" rx="18" />
                <rect className="fi" x="209" y="58" width="48" height="7" rx="3.5" />

                <circle className="k" cx="233" cy="138" r="34" />
                <path className="l" d="M218 138 L229 149 L250 126" style={{ strokeWidth: 3 }} />

                <rect className="fi" x="188" y="190" width="90" height="9" rx="4.5" opacity="0.8" />
                <rect className="fm" x="200" y="208" width="66" height="7" rx="3.5" />

                <rect className="p" x="176" y="236" width="114" height="44" rx="12" />
                <circle className="n" cx="196" cy="258" r="9" />
                <rect className="fm" x="212" y="250" width="50" height="6" rx="3" />
                <rect className="fb" x="212" y="261" width="34" height="6" rx="3" />

                <rect className="b" x="176" y="294" width="114" height="26" rx="13" />
            </g>

            <g className="ill-a-card">
                <g transform="rotate(12 340 280)">
                    <rect className="n" x="282" y="234" width="124" height="80" rx="12" />
                    <rect className="p" x="296" y="252" width="24" height="18" rx="4" />
                    <rect className="fi" x="296" y="290" width="60" height="5" rx="2.5" opacity="0.7" />
                </g>
            </g>

            <path className="dash" d="M40 110 C70 60 112 46 146 58" />
            <PaperPlane x={86} y={30} scale={0.72} />

            <Sparkles items={[
                ["ring", 362, 92, 9], ["dot", 388, 120, 3.5], ["sand", 92, 196, 7],
                ["dot", 66, 300, 3], ["sky", 370, 186, 5]
            ]} />
        </svg>
    );
}

/* Service card art (peeks from the card's bottom-right corner) ------------- */

export function AccountsArt({ className = "" }) {
    return (
        <svg {...svgProps(className, "0 0 180 150")}>
            <g transform="rotate(-12 90 90)">
                <rect className="b" x="22" y="48" width="132" height="84" rx="12" />
                <rect className="n" x="38" y="68" width="24" height="18" rx="4" />
                <rect className="fp" x="38" y="104" width="56" height="5" rx="2.5" opacity="0.85" />
            </g>
            <CoinStack x={148} y={150} count={3} />
            <Sparkles items={[["ring", 24, 22, 6], ["sand", 52, 10, 4], ["dot", 160, 40, 2.5]]} />
        </svg>
    );
}

export function TransfersArt({ className = "" }) {
    return (
        <svg {...svgProps(className, "0 0 180 150")}>
            <circle className="k" cx="58" cy="96" r="34" />
            <circle className="n" cx="132" cy="96" r="34" />
            <path className="l" d="M40 90 H78 L68 80 M150 104 H112 L122 114" style={{ strokeWidth: 3 }} />
            <path className="dash" d="M20 40 C50 10 92 12 112 30" />
            <PaperPlane x={110} y={8} scale={0.55} />
            <Sparkles items={[["dot", 18, 70, 2.5], ["ring", 170, 52, 5]]} />
        </svg>
    );
}

export function BillsArt({ className = "" }) {
    return (
        <svg {...svgProps(className, "0 0 180 150")}>
            <path
                className="p"
                d="M44 18 H128 V148 L118 140 L108 148 L98 140 L88 148 L78 140 L68 148 L58 140 L44 148 Z"
            />
            <path className="lt" d="M58 44 H112 M58 60 H104 M58 76 H110 M58 92 H96" />
            <rect className="fn" x="58" y="108" width="40" height="10" rx="5" />
            <circle className="b" cx="134" cy="112" r="20" />
            <path className="lw" d="M124 112 L131 119 L144 104" style={{ strokeWidth: 3 }} />
            <Sparkles items={[["ring", 26, 30, 6], ["sand", 160, 60, 5], ["dot", 22, 110, 2.5]]} />
        </svg>
    );
}

export function HistoryArt({ className = "" }) {
    return (
        <svg {...svgProps(className, "0 0 180 150")}>
            <rect className="p" x="26" y="26" width="128" height="124" rx="14" />
            <rect className="fn" x="46" y="98" width="16" height="36" rx="4" />
            <rect className="fb" x="72" y="74" width="16" height="60" rx="4" />
            <rect className="fk" x="98" y="88" width="16" height="46" rx="4" />
            <rect className="fb" x="124" y="58" width="16" height="76" rx="4" opacity="0.7" />
            <polyline className="l" points="48,70 76,50 102,60 132,38" />
            <rect className="fm" x="44" y="38" width="44" height="6" rx="3" />
            <Sparkles items={[["ring", 164, 22, 6], ["dot", 14, 60, 2.5], ["sand", 168, 110, 4]]} />
        </svg>
    );
}

/* Organic blob used for numbered steps and role avatars -------------------- */

export function Blob({ className = "", variant = 0 }) {
    const shapes = [
        "M30 3 C44 4 54 15 52 30 C50 45 38 54 24 51 C11 48 2 37 5 23 C7 11 17 2 30 3 Z",
        "M27 4 C41 2 53 12 53 27 C53 42 42 53 27 52 C13 51 3 41 4 27 C5 14 14 5 27 4 Z",
        "M26 3 C39 1 52 9 53 24 C54 40 45 53 29 53 C14 53 3 44 3 29 C3 15 13 4 26 3 Z",
        "M31 4 C45 6 54 18 51 32 C48 46 36 54 22 50 C9 46 2 34 5 21 C8 9 18 2 31 4 Z"
    ];

    return (
        <svg viewBox="0 0 56 56" className={className} aria-hidden="true" focusable="false">
            <path d={shapes[variant % shapes.length]} />
        </svg>
    );
}
