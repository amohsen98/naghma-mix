/**
 * The mark is a half-and-half dial: the outer ring is the 24-step maqam grid in brass,
 * the inner ring the 12-step Camelot wheel in cyan, the two traditions the app joins.
 */
export function Wordmark({ large = false }: { large?: boolean }) {
  const s = large ? 112 : 30;
  return (
    <span className={`wordmark${large ? ' wordmark-large' : ''}`}>
      <svg width={s} height={s} viewBox="0 0 100 100" aria-hidden="true">
        {Array.from({ length: 24 }, (_, i) => {
          const a = (i / 24) * Math.PI * 2;
          const r1 = 44;
          const r2 = i % 2 ? 40 : 36;
          return <line key={`o${i}`} x1={50 + r1 * Math.sin(a)} y1={50 - r1 * Math.cos(a)} x2={50 + r2 * Math.sin(a)} y2={50 - r2 * Math.cos(a)} className="wm-maqam" />;
        })}
        {Array.from({ length: 12 }, (_, i) => {
          const a = (i / 12) * Math.PI * 2;
          return <circle key={`k${i}`} cx={50 + 24 * Math.sin(a)} cy={50 - 24 * Math.cos(a)} r={3} className="wm-key" />;
        })}
        <circle cx="50" cy="50" r="6" className="wm-center" />
      </svg>
      <span className="wm-text">
        <span className="wm-ar" lang="ar">نغمة</span>
        <span className="wm-en">Naghma Mix</span>
      </span>
    </span>
  );
}
