import { arcs, proportions } from './graphiques';
import { top } from './tableau-de-bord.component';
import { StatsEvenement } from '../core/models';

describe('graphiques', () => {
  it('proportions : la plus grande valeur occupe 100 %', () => {
    expect(proportions([5, 10, 0])).toEqual([50, 100, 0]);
    expect(proportions([])).toEqual([]);
    expect(proportions([0, 0])).toEqual([0, 0]);
  });

  it('arcs : les parts couvrent tout le cercle, bout à bout', () => {
    const a = arcs([1, 3], 100);
    expect(a.map((x) => x.longueur)).toEqual([25, 75]);
    expect(a.map((x) => x.decalage)).toEqual([-0, -25]);
  });

  it('top : valeurs non nulles, triées, limitées', () => {
    const ev = (description: string, nbParticipants: number) => ({ description, nbParticipants }) as StatsEvenement;
    const barres = top([ev('a', 1), ev('b', 0), ev('c', 5)], (e) => e.nbParticipants, 1);
    expect(barres).toEqual([{ libelle: 'c', valeur: 5 }]);
  });
});
