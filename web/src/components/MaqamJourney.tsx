import { useI18n } from '../lib/i18n';
import { journeyStops } from '../lib/maqamHints';
import type { MaqamSection } from '../lib/types';

const mmss = (sec: number) => `${Math.floor(sec / 60)}:${String(Math.round(sec % 60)).padStart(2, '0')}`;

/** The song as a strip of maqam sections: brass where a maqam is sung, gray where nothing is. */
export function MaqamJourney({ sections }: { sections: MaqamSection[] }) {
  const { t, maqamName, lang } = useI18n();
  const total = sections[sections.length - 1]?.end ?? 0;
  if (!total) return null;
  const stops = journeyStops(sections).map(maqamName);
  const arrow = lang === 'ar' ? ' ← ' : ' → ';
  return (
    <figure className="journey">
      <p className="journey-route">{stops.join(arrow)}</p>
      <ol className="journey-strip" aria-label={t('maqamJourney')}>
        {sections.map((s) => {
          const name = s.maqam ? maqamName(s.maqam) : t('noMelody');
          const span = `${mmss(s.start)}–${mmss(s.end)}`;
          return (
            <li
              key={s.start}
              className={`journey-seg${s.maqam ? '' : ' none'}${s.uncertain ? ' unsure' : ''}`}
              style={{ flexGrow: s.end - s.start }}
              title={`${span} · ${name}${s.tonicNote ? ` (${s.tonicNote})` : ''}${s.uncertain ? ` · ${t('uncertain')}` : ''}`}
            >
              <span className="journey-name">{s.maqam ? name : ''}</span>
              <span className="sr-only">{span}</span>
            </li>
          );
        })}
      </ol>
      <div className="journey-axis muted small" aria-hidden="true">
        <span>{mmss(0)}</span>
        <span>{mmss(total)}</span>
      </div>
    </figure>
  );
}
