import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, ApiError } from '../lib/api';
import { useAuth } from '../lib/auth';
import { useI18n } from '../lib/i18n';
import type { Tier } from '../lib/types';

const PLANS: { tier: Tier; quota: number; blurb: 'planFree' | 'planDj' | 'planPro' | 'planAgency' }[] = [
  { tier: 'FREE', quota: 50, blurb: 'planFree' },
  { tier: 'DJ', quota: 1500, blurb: 'planDj' },
  { tier: 'PRO', quota: -1, blurb: 'planPro' },
  { tier: 'AGENCY', quota: -1, blurb: 'planAgency' },
];

export function PlansPage() {
  const { t, lang } = useI18n();
  const { user, refresh } = useAuth();
  const [params] = useSearchParams();
  const [message, setMessage] = useState<string | null>(null);

  // Back from checkout: pick up the new tier in a fresh token.
  useEffect(() => {
    if (params.get('paid') === '1') {
      refresh().then((u) => u && setMessage(t('upgraded', { tier: t(u.tier) })));
    }
  }, [params, refresh, t]);

  async function upgrade(tier: Tier) {
    try {
      const { checkoutUrl } = await api.checkout(tier, '/plans?paid=1');
      window.location.assign(checkoutUrl);
    } catch (e) {
      setMessage(e instanceof ApiError ? e.message : String(e));
    }
  }

  const fmt = new Intl.NumberFormat(lang === 'ar' ? 'ar-EG' : 'en-US');

  return (
    <div className="page">
      <h1>{t('plansTitle')}</h1>
      {message && <p className="notice" role="status">{message}</p>}
      <ul className="plans">
        {PLANS.map((p) => (
          <li key={p.tier} className={user?.tier === p.tier ? 'is-current' : ''}>
            <h2>{t(p.tier)}</h2>
            <p className="plan-quota">
              <span className="big-num">{p.quota < 0 ? t('unlimited') : fmt.format(p.quota)}</span>
              <span className="muted">{t('perMonth')}</span>
            </p>
            <p>{t(p.blurb)}</p>
            {user?.tier === p.tier
              ? <span className="muted">{t('currentPlan')}</span>
              : p.tier !== 'FREE' && <button className="btn" onClick={() => upgrade(p.tier)}>{t('upgrade')}</button>}
          </li>
        ))}
      </ul>
    </div>
  );
}
