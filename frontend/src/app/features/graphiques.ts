import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';

export interface Barre {
  libelle: string;
  valeur: number;
  couleur?: string;
}

/** Largeur relative de chaque barre (0 à 100 %) ; pure, testée sans navigateur. */
export function proportions(valeurs: number[]): number[] {
  const max = Math.max(0, ...valeurs);
  return valeurs.map((v) => (max > 0 ? Math.round((Math.max(0, v) / max) * 1000) / 10 : 0));
}

/** Arcs d'un anneau : longueur de chaque part sur un cercle de circonférence donnée. */
export function arcs(valeurs: number[], circonference: number): { longueur: number; decalage: number }[] {
  const total = valeurs.reduce((s, v) => s + Math.max(0, v), 0);
  let cumul = 0;
  return valeurs.map((v) => {
    const longueur = total > 0 ? (Math.max(0, v) / total) * circonference : 0;
    const arc = { longueur, decalage: -cumul };
    cumul += longueur;
    return arc;
  });
}

const PALETTE = ['#2f63ec', '#8b5cf6', '#f59e0b', '#10b981', '#ef4444', '#0ea5e9', '#64748b'];

/** Barres horizontales en SVG, sans bibliothèque de graphiques. */
@Component({
  selector: 'app-barres',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (barres().length === 0) {
      <p class="vide">Aucune donnée.</p>
    } @else {
      <svg [attr.viewBox]="'0 0 100 ' + barres().length * 11" class="barres" role="img" [attr.aria-label]="titre()">
        @for (b of barres(); track b.libelle; let i = $index) {
          <rect x="0" [attr.y]="i * 11 + 5" width="100" height="4" rx="1.4" fill="#eef2f8" />
          <rect x="0" [attr.y]="i * 11 + 5" [attr.width]="largeurs()[i]" height="4" rx="1.4"
                [attr.fill]="b.couleur || couleur(i)" />
          <text x="0" [attr.y]="i * 11 + 3.8" class="lib">{{ b.libelle }}</text>
          <text x="100" [attr.y]="i * 11 + 3.8" text-anchor="end" class="val">{{ b.valeur | number: format() }}{{ unite() }}</text>
        }
      </svg>
    }
  `,
  styles: `
    .barres { width: 100%; height: auto; display: block; }
    .lib { font-size: 3.6px; fill: #33415c; }
    .val { font-size: 3.6px; font-weight: 700; fill: #17233a; }
  `,
})
export class BarresComponent {
  readonly barres = input.required<Barre[]>();
  readonly titre = input('Graphique en barres');
  readonly unite = input('');
  readonly format = input('1.0-2');
  readonly largeurs = computed(() => proportions(this.barres().map((b) => b.valeur)));
  couleur(i: number): string {
    return PALETTE[i % PALETTE.length];
  }
}

/** Anneau (répartition) en SVG, avec sa légende. */
@Component({
  selector: 'app-anneau',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="anneau">
      <svg viewBox="0 0 42 42" role="img" [attr.aria-label]="titre()">
        <circle cx="21" cy="21" r="15.9155" fill="none" stroke="#eef2f8" stroke-width="6" />
        @for (a of parts(); track $index) {
          <circle cx="21" cy="21" r="15.9155" fill="none" stroke-width="6" transform="rotate(-90 21 21)"
                  [attr.stroke]="couleur($index)" [attr.stroke-dasharray]="a.longueur + ' ' + (100 - a.longueur)"
                  [attr.stroke-dashoffset]="a.decalage" />
        }
        <text x="21" y="22.5" text-anchor="middle" class="centre">{{ centre() }}</text>
      </svg>
      <ul>
        @for (b of barres(); track b.libelle; let i = $index) {
          <li><span class="pastille" [style.background]="couleur(i)"></span>{{ b.libelle }} :
            <strong>{{ b.valeur | number: '1.0-2' }}</strong></li>
        }
      </ul>
    </div>
  `,
  styles: `
    .anneau { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; }
    svg { width: 150px; height: 150px; }
    .centre { font-size: 5px; font-weight: 800; fill: #17233a; }
    ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; font-size: .9rem; }
    .pastille { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-right: 6px; }
  `,
})
export class AnneauComponent {
  readonly barres = input.required<Barre[]>();
  readonly titre = input('Répartition');
  readonly centre = input('');
  readonly parts = computed(() => arcs(this.barres().map((b) => b.valeur), 100));
  couleur(i: number): string {
    return this.barres()[i]?.couleur || PALETTE[i % PALETTE.length];
  }
}
