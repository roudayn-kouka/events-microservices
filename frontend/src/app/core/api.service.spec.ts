import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ApiService } from './api.service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('passe toujours par la Gateway (/api) avec les routes des services', () => {
    api.evenements().subscribe();
    api.participants().subscribe();
    api.logistiques().subscribe();
    api.moyennes().subscribe();
    api.statsEvenements().subscribe();
    for (const url of ['/api/events', '/api/participants', '/api/logistics', '/api/feedback/moyennes', '/api/stats/events']) {
      http.expectOne(url).flush([]);
    }
  });

  it('lit la source CQRS et l’instance dans les en-têtes du résumé', () => {
    let source: string | null = null;
    let instance: string | null = null;
    api.resume().subscribe((r) => {
      source = r.source;
      instance = r.instance;
    });
    http.expectOne('/api/stats/summary').flush(
      { nbEvents: 1 },
      { headers: { 'X-Stats-Source': 'projections-cqrs', 'X-Instance-Id': 'statistics-service:8105' } },
    );
    expect(source).toBe('projections-cqrs');
    expect(instance).toBe('statistics-service:8105');
  });

  it('inscrit un participant par un POST sans corps', () => {
    api.inscrire('uuid-1', 7).subscribe();
    const req = http.expectOne('/api/events/uuid-1/participants/7');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeNull();
    req.flush({});
  });

  it('envoie un avis à /feedback/{event}/{participant}', () => {
    api.donnerAvis('uuid-1', 3, { note: 4, commentaire: 'bien' }).subscribe();
    const req = http.expectOne('/api/feedback/uuid-1/3');
    expect(req.request.body).toEqual({ note: 4, commentaire: 'bien' });
    req.flush({});
  });
});
