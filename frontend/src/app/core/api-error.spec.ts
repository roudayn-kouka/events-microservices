import { HttpErrorResponse } from '@angular/common/http';
import { messageErreur } from './api-error';

describe('messageErreur', () => {
  it('reprend le message du fallback Resilience4j', () => {
    const e = new HttpErrorResponse({ status: 503, error: { message: 'participant-service : circuit ouvert' } });
    expect(messageErreur(e, 'x')).toBe('participant-service : circuit ouvert (HTTP 503)');
  });

  it('signale une Gateway injoignable', () => {
    expect(messageErreur(new HttpErrorResponse({ status: 0 }), 'x')).toContain('Gateway');
  });

  it('utilise le message par défaut sinon', () => {
    expect(messageErreur(new HttpErrorResponse({ status: 500, error: 'texte' }), 'Échec')).toBe('Échec (HTTP 500)');
  });
});
