// Parcours complet dans Chromium (Playwright, en conteneur) : make e2e. Les données de test sont supprimées à la fin.
import { chromium } from 'playwright';
const B = 'http://localhost:4200';
const out = process.argv[2];
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1366, height: 900 } });
const erreurs = [];
page.on('console', (m) => { if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) erreurs.push('console: ' + m.text()); });
page.on('pageerror', (e) => erreurs.push('page: ' + e.message));
page.on('response', (r) => { if (r.url().includes('/api/') && r.status() >= 500) erreurs.push(`HTTP ${r.status()} ${r.url()}`); });
const ok = (c, m) => { console.log((c ? 'OK   ' : 'KO   ') + m); if (!c) process.exitCode = 1; };
const tag = Date.now().toString().slice(-6);

await page.goto(B + '/statistiques'); await page.waitForSelector('.kpi');
ok((await page.locator('.kpi').count()) === 6, 'statistiques : 6 indicateurs');
ok(await page.getByText('source : projections-cqrs').isVisible(), 'statistiques : source CQRS affichée');
ok((await page.locator('app-anneau svg circle').count()) > 2, 'statistiques : anneaux SVG dessinés');
ok((await page.locator('app-barres svg rect').count()) > 2, 'statistiques : barres SVG dessinées');
await page.screenshot({ path: `${out}/1-statistiques.png`, fullPage: true });

await page.goto(B + '/participants'); await page.waitForSelector('tbody tr');
await page.getByLabel('Nom', { exact: true }).fill('UI' + tag); await page.getByLabel('Prénom', { exact: true }).fill('Test');
await page.getByRole('button', { name: 'Ajouter' }).click();
await page.getByText(`Test UI${tag} ajouté`).waitFor();
ok(true, 'participants : création');
await page.screenshot({ path: `${out}/2-participants.png`, fullPage: true });

await page.goto(B + '/evenements'); await page.waitForSelector('tbody tr');
await page.getByLabel('Description', { exact: true }).fill('Atelier UI ' + tag);
await page.getByLabel('Début', { exact: true }).fill('2027-03-01'); await page.getByLabel('Fin', { exact: true }).fill('2027-03-02');
await page.getByRole('button', { name: 'Créer' }).click();
await page.getByText(`Événement « Atelier UI ${tag} » créé`).waitFor();
ok(true, 'événements : création (commande Axon)');
await page.screenshot({ path: `${out}/3-evenements.png`, fullPage: true });

await page.getByRole('link', { name: 'Atelier UI ' + tag }).click();
await page.getByRole('heading', { name: 'Atelier UI ' + tag }).waitFor();
await page.locator('select[name=participant]').selectOption({ label: `Test UI${tag} (Invité)` });
await page.getByRole('button', { name: 'Inscrire' }).click();
await page.getByText('Participant inscrit.').waitFor();
await page.locator('td', { hasText: `Test UI${tag}` }).waitFor();
ok(true, 'détail : inscription via Feign, participant affiché');
ok((await page.locator('.alerte-degradee').count()) === 0, 'détail : pas de vue dégradée (tout est UP)');
await page.screenshot({ path: `${out}/4-detail.png`, fullPage: true });

await page.goto(B + '/logistique'); await page.waitForSelector('tbody tr');
await page.getByLabel('Description', { exact: true }).fill('Sono UI ' + tag);
await page.getByLabel('Prix unitaire', { exact: true }).fill('120'); await page.getByLabel('Quantité', { exact: true }).fill('2');
await page.getByRole('button', { name: 'Ajouter' }).click();
await page.locator('td', { hasText: 'Sono UI ' + tag }).waitFor();
ok(true, 'logistique : création');
await page.screenshot({ path: `${out}/5-logistique.png`, fullPage: true });

await page.goto(B + '/avis'); await page.waitForSelector('app-barres');
ok((await page.locator('app-barres svg rect').count()) > 0, 'avis : moyennes en barres');
await page.locator('select[name=ev]').selectOption({ label: 'Atelier UI ' + tag });
await page.locator('select[name=part]').selectOption({ label: `Test UI${tag}` });
await page.getByRole('button', { name: 'Envoyer' }).click();
const refus = page.locator('.alerte-erreur');
await refus.waitFor();
ok(/(HTTP 4\d\d)/.test(await refus.innerText()), 'avis : refusé pour un événement futur (règle métier) : ' + (await refus.innerText()));
await page.screenshot({ path: `${out}/6-avis.png`, fullPage: true });

// nettoyage : les données de test sont supprimées par l'interface
page.on('dialog', (d) => d.accept());
await page.goto(B + '/logistique'); await page.locator('tr', { hasText: 'Sono UI ' + tag }).getByRole('button', { name: 'Supprimer' }).click();
await page.locator('td', { hasText: 'Sono UI ' + tag }).waitFor({ state: 'detached' });
await page.goto(B + '/evenements'); await page.locator('tr', { hasText: 'Atelier UI ' + tag }).getByRole('button', { name: 'Supprimer' }).click();
await page.getByText(`Événement « Atelier UI ${tag} » supprimé`).waitFor();
await page.goto(B + '/participants'); await page.locator('tr', { hasText: 'UI' + tag }).getByRole('button', { name: 'Supprimer' }).click();
await page.locator('td', { hasText: 'UI' + tag }).waitFor({ state: 'detached' });
ok(true, 'nettoyage : logistique, événement et participant de test supprimés');

ok(erreurs.length === 0, 'aucune erreur console ni HTTP 5xx' + (erreurs.length ? ' : ' + erreurs.join(' | ') : ''));
await browser.close();
