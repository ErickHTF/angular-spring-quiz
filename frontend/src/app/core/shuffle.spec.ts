import { shuffleChoices } from './shuffle';
import type { Choice } from './models';

const choices: Choice[] = [
  { id: 'a', label: 'A', position: 1 },
  { id: 'b', label: 'B', position: 2 },
  { id: 'c', label: 'C', position: 3 },
  { id: 'd', label: 'D', position: 4 },
];

describe('shuffleChoices', () => {
  it('keeps the same order for the same player and question', () => {
    expect(shuffleChoices(choices, 'player-1:question-1')).toEqual(shuffleChoices(choices, 'player-1:question-1'));
  });

  it('usually produces a different order for different players', () => {
    const first = shuffleChoices(choices, 'player-1:question-1').map((choice) => choice.id);
    const second = shuffleChoices(choices, 'player-2:question-1').map((choice) => choice.id);
    expect(first).not.toEqual(second);
  });

  it('keeps choice ids and assigns visual positions', () => {
    const shuffled = shuffleChoices(choices, 'player-1:question-1');
    expect(shuffled.map((choice) => choice.id).sort()).toEqual(['a', 'b', 'c', 'd']);
    expect(shuffled.map((choice) => choice.position).sort()).toEqual([1, 2, 3, 4]);
  });
});
